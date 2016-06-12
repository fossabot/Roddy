package roddyTools

@groovy.transform.CompileStatic
public class CallWrapper {


    public static final String ABORTED = "ABORTED"
    public static final String STARTED = "STARTED"

    public static int main(String[] args) {
        new CallWrapper().run();
    }

     Map<String, String> fakeEnvVariablesForTests

    /** Some variables, taken from env and config **/
    Map<String, String> cvalues

    String lockCommand = "lockfile -s 1 -r 50"

    String unlockCommand = "rm -f"

    String jobStateLogFile = "";

    String jobStateLogsLockFile = "";

    boolean RODDY_AUTOCLEANUP_SCRATCH = false;

    String RODDY_JOBID = "";

    String RODDY_SCRATCH = "";

    String RODDY_PARENT_JOBS = "";

    String TOOL_ID = "";

    String WRAPPED_SCRIPT = "";

    String LD_LIBRARY_PATH = "";

    String JOB_PROFILER_BINARY = "";

    String outputFileGroup = "";

    boolean enableJobProfiling = false;

    boolean runDockerized = false;

    boolean useLockfile

    // Load the config file
    Map<String, String> loadConfigurationValues() {
        return new File(cvalues["CONFIG_FILE"]).readLines().collectEntries { String line -> def split = line.trim().split("[=]"); [split[0], split.size() > 1 ? split[1..-1].join("=") : ""] }
    }

    // Load and correct all environment variables
    // Also load from parameter file, if applicable
    Map<String, String> getEnvironmentVariables() {
        Map<String, String> cvalues = fakeEnvVariablesForTests + System.getenv()
        RODDY_PARENT_JOBS = cvalues["RODDY_PARENT_JOBS"] ?: ""
        String parameterFile = cvalues["PARAMETER_FILE"] ?: ""
        if (parameterFile)
            cvalues += new File(parameterFile).readLines().collectEntries() { String line -> def split = line.replace("export ", "").split("[=]"); [split[0], split.size() > 1 ? split[1..-1].join("=") : ""] } + cvalues
        return cvalues;
    }

    void checkAndSetJobVariables() {
        // This is much more complicated than it should be... Maybe because of a problem in IDEA
        RODDY_JOBID = cvalues["RODDY_JOBID"] ?: ['/bin/bash', '-c', 'echo \$\$'].execute().text.replace("\n", "")

        String defaultScratchDir = cvalues["defaultScratchDir"] ?: "/data/roddyScratch"
        RODDY_SCRATCH = cvalues["RODDY_SCRATCH"] ?: "${defaultScratchDir}/${RODDY_JOBID}"
        File f = new File(RODDY_SCRATCH);
    }

    void fillVariables() {
        def errors = []
        cvalues = getEnvironmentVariables();
        cvalues += loadConfigurationValues();
        checkAndSetJobVariables();

        TOOL_ID = cvalues["TOOL_ID"];
        WRAPPED_SCRIPT = cvalues["WRAPPED_SCRIPT"];
        enableJobProfiling = cvalues["enableJobProfiling"] ? Boolean.parseBoolean(cvalues["enableJobProfiling"]) : false;
        JOB_PROFILER_BINARY = cvalues["JOB_PROFILER_BINARY"]
        jobStateLogFile = cvalues["jobStateLogFile"];
        outputFileGroup = cvalues["outputFileGroup"] ?: 'groups'.execute().text.split("[ ]")[0]

        if (!RODDY_JOBID) errors << "The job id for this job is not available, RODDY_JOBID is not set and could not be determined."
        if (!RODDY_SCRATCH) errors << "The scratch dir is not available, RODDY_SCRATCH is not set and could not be determined."
        if (!TOOL_ID) errors << "The variable 'TOOL_ID' is not set."
        if (!WRAPPED_SCRIPT) errors << "The variable 'WRAPPED_SCRIPT' is not set."
        if (!jobStateLogFile) errors << "The variable 'jobStateLogFile' is not set"
        if (enableJobProfiling && !JOB_PROFILER_BINARY) errors << "Job profiling is requested but JOB_PROFILER_BINARY is not set."

        if (errors) {
            throw new RuntimeException("Some necessary variables are not set, aborting:\n" + errors.join("\n"));
        }

        println("Read out variables and environment, script can start now.")
    }

    // Define some methods first, they are mostly in their call order.
    int exec(String command, boolean full = true) {
        //TODO What an windows systems?
        Process process = null;
        if (full)
            process = ["bash", "-c", command].execute();
        else
            process = command.execute();
        final String separator = "\n"

        process.waitForProcessOutput(System.out, System.out);
        process.waitFor();
        if (process.exitValue() > 0) {
            def cmd = command;
            def ev = process.exitValue()
            println("Process could not be run" + separator + "\tCommand: sh -c " + cmd + separator + "\treturn code is: " + ev)
        }
        return process.exitValue();
    }

    boolean checkParentJobsAndAbortIfNecessary() {
        if (RODDY_PARENT_JOBS) {
            String jobids = RODDY_PARENT_JOBS.replace("(", "").replace(")", "").trim() // Remove prefix and suffix, if applicable
            jobids.split("[ ]").each {
                String id ->
                    // The 250 check was in the original wrapper script. I don't know why, I don't know how, keep it as a reminder?
                    //        [[ ${exitCode-} == 250 ]] && continue;
                    String status = new File(jobStateLogFile).readLines().findAll { String line -> line.startsWith(id) }?.last()?.split("[:]")[1] ?: "0"
                    if (status.toInteger() > 0) {
                        println "At least one of this parents jobs exited with an error code. This job will not run."
                    }
            }
        }
    }

    void checkAndSetLockCommand() {
        //         Check
        jobStateLogsLockFile = "$jobStateLogFile~"

        //         Select the proper lock command. lockfile-create is not tested though.
        useLockfile = true
        if (!exec("if [[ -z `which lockfile` ]]; then echo A; fi")) useLockfile = false

        if (!useLockfile) {
            lockCommand = "lockfile-create"
            unlockCommand = "lockfile-remove"
            println("Set lockfile commands to lockfile-create and lockfile-remove")
        }
    }

    /** Set LD_LIBRARY_PATH to LD_LIB_PATH, if the script was called recursively. **/
    void resetLibraryPath() {
        if (!cvalues["LD_LIB_PATH"]) LD_LIBRARY_PATH = cvalues["LD_LIB_PATH"];
    }

    boolean createJobDirectories() {
        def errors = [];

        File roddyScratch = new File(RODDY_SCRATCH)
        File temp = new File(cvalues["DIR_TEMP"])
        if (!roddyScratch.exists()) roddyScratch.mkdir()
        if (!temp.exists()) temp.mkdir();

        if (!roddyScratch.exists()) errors << "Could not create directory: ${roddyScratch}";
        if (!temp.exists()) errors << "Could not create temporary directory: ${temp}";

        if (errors) {
            println(errors.join("\n"))
            throw new IOException(errors.join("\n"));
        }
    }

    String createExportsForEnvironmentVariables() {
        getEnvironmentVariables().collect { String k, String v -> "export $k=${v.replace("-x", "").replace('#', '$')}; " }.join("")
    }

    void appendToJobStateLogFile(String code) {
        // Rely on Linux / Bash to handle the file locks correctly.
        if (!jobStateLogFile)
            throw new RuntimeException("Could not add line to jobstate logfile, the variable is not set!")
        String cmd =
                """
                    ${lockCommand} ${jobStateLogsLockFile};
                    echo "${RODDY_JOBID}:${code}:"`date +"%s"`":${TOOL_ID}" >> ${jobStateLogFile};
                    ${unlockCommand} ${jobStateLogsLockFile};
                """
        println ""
        exec(cmd);

    };

    int runWrappedScript() {
        println("Calling script ${WRAPPED_SCRIPT}");

        // Call the tool, add variables, correct exports
        String command = "";

        // Create exports for call
        if (LD_LIBRARY_PATH)
            command += "export $LD_LIBRARY_PATH; "
        command += "export $WRAPPED_SCRIPT; "
        command += createExportsForEnvironmentVariables()
        command += "export outputFileGroup=$outputFileGroup; "

        // Put in profiler
        if (JOB_PROFILER_BINARY && enableJobProfiling)
            command += JOB_PROFILER_BINARY

        // Append the called command
        command += " bash -c " + cvalues["WRAPPED_SCRIPT"];

        // And execute...
        int exitCode = exec(command);

        println("Exited script ${WRAPPED_SCRIPT} with value ${exitCode}")

        Thread.sleep(2000);
        return exitCode;
    }

    void performAutocleanup() {
        // Set this in your command factory class, when roddy should clean up the dir for you.
        if (RODDY_AUTOCLEANUP_SCRATCH) (new File(RODDY_SCRATCH)).deleteDir();
    }

    int performFinalChecks(int code) {
        if (code == 0) System.exit(0);
        if (code == 100) {
            println "Wrapped script '${WRAPPED_SCRIPT}' exited with code 100. Finishing script with 99 for compatibility reasons with Sun Grid Engine. 100 is reserved for SGE usage."
            return 99;
        }
        return code;
    }

    CallWrapper() {}

    int run() {

        try {
            fillVariables()

            // Check first, if we should run this script in a docker

            // Check if we need to change group and call again

            // Seems we can run the real stuff...

            // Perform initial tests
            checkParentJobsAndAbortIfNecessary()

            // Setup environment
            checkAndSetLockCommand()
            resetLibraryPath()
            checkAndSetJobVariables()

            // Create directories
            createJobDirectories()
        } finally {
            appendToJobStateLogFile(ABORTED);
        }

        int exitcode = 240;

        try {
            // Run the job
            appendToJobStateLogFile(STARTED);

            exitcode = performFinalChecks(runWrappedScript());

            // Finalize
            performAutocleanup()
        } finally {
            appendToJobStateLogFile("" + exitcode);
        }

        return exitcode;
    }

}