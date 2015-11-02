package de.dkfz.roddy.plugins

import de.dkfz.roddy.AvailableFeatureToggles
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.client.RoddyStartupModes
import de.dkfz.roddy.client.cliclient.CommandLineCall
import de.dkfz.roddy.execution.io.ExecutionHelper
import de.dkfz.roddy.tools.*
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.core.Initializable
import groovy.transform.TypeCheckingMode

/**
 * Factory to load and integrate plugins.
 */
@groovy.transform.CompileStatic
public class LibrariesFactory extends Initializable {
    private static LoggerWrapper logger = LoggerWrapper.getLogger(LibrariesFactory.class.getName());

    private static LibrariesFactory librariesFactory;

    public static URLClassLoader urlClassLoader
    public static GroovyClassLoader centralGroovyClassLoader;

    public static final String PLUGIN_VERSION_CURRENT = "current";
    public static final String PLUGIN_BASEPLUGIN = "PluginBase";
    public static final String BUILDINFO_DEPENDENCY = "dependson";
    public static final String BUILDINFO_COMPATIBILITY = "compatibleto";
    public static final String BUILDINFO_TEXTFILE = "buildinfo.txt";
    public static final String BUILDINFO_STATUS = "status"
    public static final String BUILDINFO_STATUS_BETA = "beta"

    private List<String> loadedLibrariesInfo = [];

    private List<PluginInfo> loadedPlugins = [];

    private Map<PluginInfo, File> loadedJarsByPlugin = [:]

    private Map<String, Map<String, PluginInfo>> mapOfPlugins = [:];

    private boolean librariesAreLoaded = false;

    private SyntheticPluginInfo synthetic

    /**
     * The synthetic plugin info object is solely used for automatically created synthetic file classes.
     * It is not usable for other purposes.
     */
    private static class SyntheticPluginInfo extends PluginInfo {

        SyntheticPluginInfo(String name, File zipFile, File directory, File developmentDirectory, String prodVersion, Map<String, String> dependencies) {
            super(name, zipFile, directory, developmentDirectory, prodVersion, dependencies)
        }

        private Map<String, Class> map = [:]

        public void addClass(Class cls) {
            map[cls.name] = cls;
            map[cls.simpleName] = cls;
        }
    }

    /**
     * This resets the singleton and is not thread safe!
     * Actually only creates a new singleton clearing out old values.
     * @return
     */
    public static LibrariesFactory initializeFactory(boolean enforceinit = false) {
        if (!librariesFactory || enforceinit)
            librariesFactory = new LibrariesFactory();
        return librariesFactory;
    }

    private LibrariesFactory() {
        synthetic = new SyntheticPluginInfo("Synthetic", null, null, null, "current", [:]);
    }

    public SyntheticPluginInfo getSynthetic() {
        return synthetic;
    }

    public static GroovyClassLoader getGroovyClassLoader() {
        if (centralGroovyClassLoader == null) {
            centralGroovyClassLoader = new GroovyClassLoader(ClassLoader.getSystemClassLoader())
            urlClassLoader = centralGroovyClassLoader;
        }
        return centralGroovyClassLoader;
    }

    private Map<PluginInfo, List<String>> classListCacheByPlugin = [:];

    public Class searchForClass(String name) {
        if (name.contains(".")) {
            return getGroovyClassLoader().loadClass(name);
        } else {
            //Search synthetic classes first.
            if (getSynthetic().map.containsKey(name))
                return getSynthetic().map[name];

            // SEVERE TODO This is a very quick hack and heavily depends on the existens of jar on the system!
            List<String> listOfClasses = []
            synchronized (loadedPlugins) {
                loadedPlugins.each {
                    PluginInfo plugin ->
                        if (!classListCacheByPlugin.containsKey(plugin)) {
                            String text = ExecutionHelper.execute("jar tvf ${loadedJarsByPlugin[plugin]}")
                            classListCacheByPlugin[plugin] = text.readLines();
                        }
                        classListCacheByPlugin[plugin].each {
                            String line ->
                                if (!line.endsWith(".class")) return;
                                String cls = line.split("[ ]")[-1][0..-7];
                                if (cls.endsWith("/" + name)) {
                                    cls = cls.replace("/", ".");
                                    cls = cls.replace("\\", ".");
                                    synchronized (listOfClasses) {
                                        listOfClasses << cls;
                                    }
                                }
                        }
                }
            }
            if (listOfClasses.size() > 1) {
                logger.severe("Too many available classes, please specify fully, choosing one of the following: ")
                listOfClasses.each { logger.severe("  " + it) }
                return null;
            }
            if (listOfClasses.size() == 1) {
                return getGroovyClassLoader().loadClass(listOfClasses[0]);
            }
            logger.severe("No class found for ${name}")
            return null;
        }
    }
/**
 * Resolve all used / necessary plugins and also look for miscrepancies.
 * @param usedPlugins
 */
    public boolean resolveAndLoadPlugins(String[] usedPlugins) {
        Map<String, Map<String, PluginInfo>> mapOfPlugins = loadMapOfAvailablePlugins();
        loadLibraries(buildupPluginQueue(mapOfPlugins, usedPlugins) as List);
        librariesAreLoaded = true;
        return true;
    }

    public static Map<String, PluginInfo> buildupPluginQueue(Map<String, Map<String, PluginInfo>> mapOfPlugins, String[] usedPlugins) {
        List<String> usedPluginsCorrected = [];
        Map<String, String> pluginsToCheck = usedPlugins.collectEntries { String requestedPlugin ->
            List<String> pSplit = requestedPlugin.split("[:-]") as List;
            String id = pSplit[0];
            String version = pSplit[1] ?: "current";
            String revision = pSplit[2] ?: "0"
            usedPluginsCorrected << [id, version + "-" + revision].join(":");
            [id, version + "-" + revision];
        }
        usedPlugins = usedPluginsCorrected;

        Map<String, PluginInfo> pluginsToActivate = [:];
        while (pluginsToCheck.size() > 0) {

            String id = pluginsToCheck.keySet()[0]
            String version = pluginsToCheck[id]
            if (version != "current" && !version.contains("-")) version += "-0";
            pluginsToCheck.remove(id, version);

            if (!mapOfPlugins[id] || !mapOfPlugins[id][version]) {
                logger.severe("The plugin ${id}:${version} could not be found, are the plugin paths properly set?");
                return null;
            }

            // Set pInfo to a valid instance.
            PluginInfo pInfo = mapOfPlugins[id][version];

            // Now, if the plugin is not in usedPlugins (and therefore not fixed), we search the newest compatible
            // version of it which may either be a revision (x:x.y-[0..n] or a higher compatible version.
            // Search the last valid entry in the chain.
            if (!usedPlugins.contains("${id}:${version}")) {
                for (; pInfo.nextInChain != null; pInfo = pInfo.nextInChain) {
                    version = pInfo.prodVersion;
                }
            }

            if (pInfo == null)
                pInfo = mapOfPlugins[id][PLUGIN_VERSION_CURRENT]
            if (pInfo == null)
                continue;
            if (pluginsToActivate[id] != null) {
                if (pluginsToActivate[id].prodVersion != version) {
                    logger.severe("There is a version mismatch for plugin dependencies! Not starting up.");
                    return null;
                } else {
                    //Not checking again!
                }
            } else {
                Map<String, String> dependencies = pInfo.getDependencies()
                dependencies.each { String k, String v ->
                    if (v != "current" && !v.contains("-")) v += "-0";
                    pluginsToCheck[k] = v;
                }
//                pluginsToCheck.putAll(dependencies);
                pluginsToActivate[id] = pInfo;
            }
        }
        return pluginsToActivate;
    }

    public boolean areLibrariesLoaded() {
        return librariesAreLoaded
    }

    public List<PluginInfo> getLoadedPlugins() {
        return loadedPlugins;
    }

    /**
     * This method returns a list of all plugins found in plugin directories.
     * This method distinguishes between Roddy environments for development and packed Roddy versions.
     * If the user is a developer, the development directories are set and preferred over bundled libraries.
     * * Why is this necessary?
     * - If you develop scripts and plugins, you possibly use a version control system. However the scripts are copied to the
     *   plugin directory on compilation (and possibly out of version control. So Roddy tries to take the "original" version
     *   which resides in the plugins project space.
     * * Where can plugins be found?
     * - In the dist/libraries folder (non developer)
     * - In any other configured folder. You as the developer has to set external projects up in the configuration. (developer)
     *
     * @return
     */
    public Map<String, Map<String, PluginInfo>> loadMapOfAvailablePlugins() {
        if (mapOfPlugins.size() > 0) {
            return mapOfPlugins;
        }

        //Search all plugin folders and also try to join those if possible.
        List<Tuple2<File, String[]>> collectedPluginDirectories = [];
        List<File> pluginDirectories = Roddy.getPluginDirectories();
        def blacklist = [".idea", "out", "Template", ".svn"]

        for (File pBaseDirectory : pluginDirectories) {
            File[] directoryList = pBaseDirectory.listFiles().sort() as File[];
            for (File pEntry in directoryList) {
                String dirName = pEntry.getName();
                boolean isZip = dirName.endsWith(".zip");
                if (isZip)
                    dirName = dirName[0..-5]; // Remove .zip from the end.
                String[] splitName = dirName.split(StringConstants.SPLIT_UNDERSCORE); //First split for .zip then for the version
                String pluginName = splitName[0];
                if ((!pEntry.isDirectory() && !isZip) || !pluginName || blacklist.contains(pluginName))
                    continue;
                collectedPluginDirectories << new Tuple2<File, String[]>(pEntry, splitName);
            }
        }

        mapOfPlugins = loadPluginsFromDirectories(collectedPluginDirectories)
        return mapOfPlugins
    }

    /**
     * Loads all available plugins (including revisions and versions) from a set of directories.
     * Type checking mode is set to skip for this method, because Groovy does not likes Generics and Comparisons in a combination...
     * return tLeft.x.compareTo(tRight.x) * 10000 +
     * tLeft.y.compareTo(tRight.y) * 1000 +
     * tLeft.z.compareTo(tRight.z) * 100 +
     * tLeft.w.compareTo(tRight.w) * 10 +
     * tLeft.q.compareTo(tRight.q) * 1;
     *
     * Will not work!
     *
     * @param collectedPluginDirectories
     * @return
     */
    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private static Map<String, Map<String, PluginInfo>> loadPluginsFromDirectories(List<Tuple2<File, String[]>> collectedPluginDirectories) {
        //First, check, if a plugin name is valid or not.
        List<Tuple2<File, String[]>> collectedTemporary = [];
        collectedPluginDirectories.each { tuple ->
            String rev = (tuple.x.name.split("[-]") as List)[1];
            if (rev?.isNumber() || !rev) collectedTemporary << tuple
            else logger.severe("Filtered out plugin ${tuple.x.name}, as the revision id is not numeric.")
        }
        collectedPluginDirectories = collectedTemporary;

        //Make sure, that the plugin directories are properly sorted before we start. This is especially useful
        // if we search for revisions and extensions.
        collectedPluginDirectories = collectedPluginDirectories.sort {
            Tuple2<File, String[]> left, Tuple2<File, String[]> right ->
                List<String> splitLeft = left.x.name.split("[_:.-]") as List;
                List<String> splitRight = right.x.name.split("[_:.-]") as List;
                Tuple5<String, Integer, Integer, Integer, Integer> tLeft = new Tuple5<>(
                        splitLeft[0],
                        (splitLeft[1] ?: Integer.MAX_VALUE) as Integer,
                        (splitLeft[2] ?: Integer.MAX_VALUE) as Integer,
                        (splitLeft[3] ?: Integer.MAX_VALUE) as Integer,
                        (splitLeft[4] ?: 0) as Integer
                );
                Tuple5<String, Integer, Integer, Integer, Integer> tRight = new Tuple5<>(
                        splitRight[0],
                        (splitRight[1] ?: Integer.MAX_VALUE) as Integer,
                        (splitRight[2] ?: Integer.MAX_VALUE) as Integer,
                        (splitRight[3] ?: Integer.MAX_VALUE) as Integer,
                        (splitRight[4] ?: 0) as Integer
                );
                if (tLeft.x != tRight.x) return tLeft.x.compareTo(tRight.x);
                if (tLeft.y != tRight.y) return tLeft.y.compareTo(tRight.y);
                if (tLeft.z != tRight.z) return tLeft.z.compareTo(tRight.z);
                if (tLeft.w != tRight.w) return tLeft.w.compareTo(tRight.w);
                if (tLeft.q != tRight.q) return tLeft.q.compareTo(tRight.q);
                return 0;
        }
        Map<String, Map<String, PluginInfo>> _mapOfPlugins = [:];
        for (Tuple2<File, String[]> _entry : collectedPluginDirectories) {
            File pEntry = _entry.x;
            String[] splitName = _entry.y;//pEntry.getName().split(StringConstants.SPLIT_UNDERSCORE); //First split for .zip then for the version

            String pluginName = splitName[0];
            String[] pluginVersionInfo = splitName.length > 1 ? splitName[1].split(StringConstants.SPLIT_MINUS) : [PLUGIN_VERSION_CURRENT] as String[];
            String pluginVersion = pluginVersionInfo[0];
            String pluginRevision = pluginVersionInfo.length > 1 ? pluginVersionInfo[1] : "0";
            String pluginFullVersion = pluginVersion + "-" + pluginRevision;
            if (pluginVersion == "current") pluginFullVersion = "current";

            int revisionNumber = pluginRevision as Integer;

            CommandLineCall clc = Roddy.getCommandLineCall();
            boolean helpMode = clc ? clc.startupMode == RoddyStartupModes.help : false

            File develEntry = null;
            File prodEntry = null;
            File zipFile = null;

            if (pEntry.getName().endsWith(".zip")) { // Zip files are handled differently and cannot be checked for contents!
                zipFile = pEntry;
                if (Roddy.getFeatureToggleValue(AvailableFeatureToggles.UnzipZippedPlugins)) {
                    if (!new File(zipFile.getAbsolutePath()[0..-5]).exists()) {
                        if (!helpMode) logger.postAlwaysInfo("Unzipping zipped plugin (this is done unchecked and unlocked, processes could interfere with each other!)")
                        (new RoddyIOHelperMethods.NativeLinuxZipCompressor()).decompress(zipFile, null, zipFile.getParentFile());
                    }
                }
                continue;
            }

            if (zipFile != null) { // Only "releases" / packages have a zip file and need not to be dissected further.
                continue;
            }

            // TODO Sort file list before validation?
            // TODO Get version also from directory in case that the zip file is missing?

            File prodJarFile = pEntry.listFiles().find { File f -> f.name.endsWith ".jar"; }
            if (prodJarFile) {
                prodEntry = pEntry;
            }

            File devSrcPath = pEntry.listFiles().find { File f -> f.name == "src"; }
            if (devSrcPath) {
                develEntry = pEntry;
            }

            if (!prodEntry && !develEntry) { //Now we might have a plugin without a jar file. This is allowed to happen since 2.2.87
                prodEntry = pEntry;
            }


            def pluginMap = _mapOfPlugins.get(pluginName, new LinkedHashMap<String, PluginInfo>())

            PluginInfo previousPlugin = pluginMap.values().size() > 0 ? pluginMap.values().last() : null;
            boolean isRevisionOfPlugin = previousPlugin?.getMajorAndMinor() == pluginVersion && previousPlugin?.getRevision() == revisionNumber - 1;
            boolean isCompatible = false;
            boolean isBetaPlugin = false

            //Get dependency list from plugin
            Map<String, String> pluginDependencies = [:];
            File buildinfoFile = pEntry.listFiles().find { File f -> f.name == BUILDINFO_TEXTFILE };
            if (buildinfoFile) {
                for (String line in buildinfoFile.readLines()) {
                    List<String> split = ((line.split(StringConstants.SPLIT_EQUALS) as List)[1]?.split(StringConstants.SPLIT_COLON)) as List;
                    if (line.startsWith(BUILDINFO_DEPENDENCY)) {
                        String workflow = split[0];
                        String version = split.size() > 1 ? split[1] : PLUGIN_VERSION_CURRENT;
                        pluginDependencies.put(workflow, version);
                    } else if (line.startsWith(BUILDINFO_COMPATIBILITY)) {
                        // Not revision but compatible. Check, if the former plugin id (excluding the revision number) is
                        // set as compatible.
                        if (!split[0].contains("-")) split[0] = split[0] + "-0";
                        if (previousPlugin?.getProdVersion() == split[0])
                            isCompatible = true;
                        else
                            logger.info("Could not find entry for compatibility ${split[0]}");
                    } else if (line.startsWith(BUILDINFO_STATUS)) {
                        if (split[0] == BUILDINFO_STATUS_BETA) {
                            isBetaPlugin = true;
                        }
                    }
                }
                if (pluginName != "DefaultPlugin" && !pluginDependencies.containsKey(PLUGIN_BASEPLUGIN))
                    pluginDependencies.put(PLUGIN_BASEPLUGIN, PLUGIN_VERSION_CURRENT);
                if (pluginName != "DefaultPlugin" && !pluginDependencies.containsKey("DefaultPlugin"))
                    pluginDependencies.put("DefaultPlugin", PLUGIN_VERSION_CURRENT);
            }


            PluginInfo newPluginInfo = new PluginInfo(pluginName, zipFile, prodEntry, develEntry, pluginFullVersion, pluginDependencies)
            pluginMap[pluginFullVersion] = newPluginInfo;
            if (isRevisionOfPlugin || isCompatible) {
                newPluginInfo.previousInChain = previousPlugin;
                previousPlugin.nextInChain = newPluginInfo;
            }

            if (isRevisionOfPlugin)
                newPluginInfo.previousInChainConnectionType = PluginInfo.PluginInfoConnection.REVISION;
            else if (isCompatible)
                newPluginInfo.previousInChainConnectionType = PluginInfo.PluginInfoConnection.EXTENSION;

            if (isBetaPlugin)
                newPluginInfo.isBetaPlugin = true;
        }
        return _mapOfPlugins
    }

    /**
     * Get a list of all available plugins in their most recent version...
     * @return
     */
    public List<PluginInfo> getAvailablePluginVersion() {
        List<PluginInfo> mostCurrentPlugins = [];
        Map<String, Map<String, PluginInfo>> availablePlugins = loadMapOfAvailablePlugins();
        availablePlugins.each {
            String pluginID, Map<String, PluginInfo> versions ->
                if (versions.keySet().contains(PLUGIN_VERSION_CURRENT))
                    mostCurrentPlugins << versions[PLUGIN_VERSION_CURRENT];
                else
                    mostCurrentPlugins << versions[versions.keySet().last()]
        }

        return mostCurrentPlugins;
    }

    public static boolean addFile(File f) throws IOException {
        return addURL(f.toURI().toURL());
    }

    /**
     * The following method adds a jar file to the current classpath.
     * The code is initially taken from here:
     * http://stackoverflow.com/questions/60764/how-should-i-load-jars-dynamically-at-runtime
     * Beware that classes must only be added once due to several constrictions.
     * See the mentioned site for more information.
     *
     * @param u
     * @throws IOException
     */
    public static boolean addURL(URL u) throws IOException {
        try {
            getGroovyClassLoader().addURL(u);
            return true;
        } catch (Throwable t) {
            logger.severe("A plugin could not be loaded: " + u)
            return false;
        }
    }

    public static LibrariesFactory getInstance() {
        if (librariesFactory == null) {
            logger.postAlwaysInfo("The libraries factory for plugin management was not initialized! Creating a new, empty object.")
            librariesFactory = new LibrariesFactory();
        }

        return librariesFactory;
    }

    public void loadLibraries(List<PluginInfo> pluginInfo) {
        pluginInfo.parallelStream().each { PluginInfo pi ->
            if (!pi.directory)
                return;
            File jarFile = pi.directory.listFiles().find { File f -> f.name.endsWith(".jar") };
            if (jarFile && !addFile(jarFile))
                return;

            def loadInfo = "The plugin ${pi.getName()} [ Version: ${pi.getProdVersion()} ] was loaded."
            logger.postAlwaysInfo(loadInfo)
            synchronized (loadedLibrariesInfo) {
                loadedLibrariesInfo << loadInfo.toString()
                loadedJarsByPlugin[pi] = jarFile;
            }
        }
    }

    public List<String> getLoadedLibrariesInfoList() {
        return loadedLibrariesInfo;
    }

    public Class tryLoadClass(String className) throws ClassNotFoundException {
        try {
            return loadClass(className);

        } catch (any) {
            logger.severe("Could not load class className");
            return null;
        }
    }

    public Class loadClass(String className) throws ClassNotFoundException {
        return getGroovyClassLoader().loadClass(className);
    }

    @Override
    public boolean initialize() {
    }

    @Override
    public void destroy() {
    }
}
