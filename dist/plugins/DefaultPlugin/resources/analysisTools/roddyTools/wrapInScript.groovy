#!/usr/bin/env /home/heinold/.roddy/runtimeDevel/groovy/bin/groovy
import groovy.transform.Field

// Shebang line or groovy call??? Maybe just a synthetic script passed via pipe?

// Some variables, taken from env and config
@Field Map<String, String> cvalues
@Field String lockCommand = "";
@Field String unlockCommand = "";
@Field String jobStateLogFile = "";
@Field String jobStateLogsLockFile = "";
@Field String RODDY_JOBID = "";
@Field String RODDY_SCRATCH = "";
@Field String TOOL_ID = "";
@Field String WRAPPED_SCRIPT = "";
@Field String LD_LIBRARY_PATH = "";
@Field String JOB_PROFILER_BINARY = "";
@Field String outputFileGroup = "";
@Field boolean enableJobProfiling = false;
@Field boolean runDockerized = false;

// Load the config file
Map<String, String> loadConfigurationValues() {
    return new File(cvalues["CONFIG_FILE"]).readLines().collectEntries { String line -> def split = line.split("[=]"); [split[0], split.size() > 1 ? split[1..-1].join("=") : ""] }
}

// Load and correct all environment variables
// Also load from parameter file, if applicable
Map<String, String> getEnvironmentVariables() {
    jobid = RODDY_JOBID.split("[.]")[0]
    Map<String, String> cvalues = System.getenv()
//    cvalues.findAll { String k, String v -> v.contains("RODDY_JOBID") || v.contains("-x") }.collectEntries {
//        String k, String v ->
//            [k, v]
//    }
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
    if (!cvalues["RODDY_SCRATCH"])
        RODDY_SCRATCH = "${defaultScratchDir}/${RODDY_JOBID}"
    File f = new File(RODDY_SCRATCH);
}

void fillVariables() {
    cvalues = getEnvironmentVariables();
    cvalues += loadConfigurationValues();
    checkAndSetJobVariables();
    TOOL_ID = cvalues["TOOL_ID"];
    WRAPPED_SCRIPT = cvalues["WRAPPED_SCRIPT"];
    JOB_PROFILER_BINARY = cvalues["JOB_PROFILER_BINARY"]
    jobStateLogFile = cvalues["jobStateLogFile"];
    enableJobProfiling = cvalues["enableJobProfiling"] ? Boolean.parseBoolean(cvalues["enableJobProfiling"]) : false;
    outputFileGroup = cvalues["outputFileGroup"] ?: 'groups'.execute().text.split("[ ]")[0]
}

fillVariables()
println("Read out variables and environment, now starting script.")
// Check first, if we should run this script in a docker

// Check if we need to change group and call again

// Seems we can run the real stuff...

// Define some methods first, they are mostly in their call order.
int exec(String command, boolean full = true) {
    //TODO What an windows systems?
    def process = null;
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
    //        # Check if the jobs parent jobs are stored and passed as a parameter. If so Roddy checks the job state logfile
//        # if at least one of the parent jobs exited with a value different to 0.
//        if [[ ! ${RODDY_PARENT_JOBS} = false ]]
//        then
//        # Now check all lines in the file
//        strlen=`expr ${#RODDY_PARENT_JOBS} - 2`
//        RODDY_PARENT_JOBS=${RODDY_PARENT_JOBS:1:strlen}
//        for parentJob in ${RODDY_PARENT_JOBS[@]}; do
//        [[ ${exitCode-} == 250 ]] && continue;
//        result=`cat ${jobStateLogFile} | grep -a "^${parentJob}:" | tail -n 1 | cut -d ":" -f 2`
//        [[ ! $result -eq 0 ]] && echo "At least one of this parents jobs exited with an error code. This job will not run." && startCode="ABORTED"
//        done
//        fi

    //        [[ ${startCode} == 60000 || ${startCode} == "ABORTED" ]] && echo "Exitting because a former job died." && exit 250
//        # Sleep a second before and after executing the wrapped script. Allow the system to get different timestamps.
//                sleep 2

}

void checkWrapperScriptAndAbortIfNecessary() {
//        # Check the wrapped script for existence
//        [[ ${WRAPPED_SCRIPT-false} == false || ! -f ${WRAPPED_SCRIPT} ]] && startCode=ABORTED && echo "The wrapped script is not defined or not existing."

}

void checkAndSetLockCommand() {
    //         Check
    jobStateLogsLockFile = "$jobStateLogFile~"

    //         Select the proper lock command. lockfile-create is not tested though.
    lockCommand = "lockfile -s 1 -r 50"
    unlockCommand = "rm -f"

    useLockfile = true
    if (!exec("if [[ -z `which lockfile` ]]; then echo A; fi")) useLockfile = false
//        [[ ${useLockfile} == false ]] && lockCommand=lockfile-create && unlockCommand=lockfile-remove && echo "Set lockfile commands to lockfile-create and lockfile-remove"
}


void resetLibraryPath() {
    // Set LD_LIBRARY_PATH to LD_LIB_PATH, if the script was called recursively.
    if (!cvalues["LD_LIB_PATH"]) LD_LIBRARY_PATH = cvalues["LD_LIB_PATH"];
//    [[ ${debugWrapInScript-false} == true ]] && set -xv
//    [[ ${debugWrapInScript-false} == false ]] && set +xv*/
}

void createJobDirectories() {
    File roddyScratch = new File(RODDY_SCRATCH)
    File temp = new File(cvalues["DIR_TEMP"])
    if (!roddyScratch.exists()) roddyScratch.mkdir()
    if (!temp.exists()) temp.mkdir();
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
    // Create export variables.

    // Call the tool, add variables, correct exports
    //
    String command = "";
    if (LD_LIBRARY_PATH)
        command += "export $LD_LIBRARY_PATH; "
    command += "export $WRAPPED_SCRIPT; "
    command += getEnvironmentVariables().collect { String k, String v -> "export $k=${v.replace("-x", "").replace('#', '$')}; " }.join()
    command += "export outputFileGroup=$outputFileGroup; "
    if (JOB_PROFILER_BINARY && enableJobProfiling)
        command += JOB_PROFILER_BINARY
    command += " bash -c " + cvalues["WRAPPED_SCRIPT"];
    int exitCode = exec(command);

    println("Calling script ${WRAPPED_SCRIPT}");

//        myGroup=`groups  | cut -d " " -f 1`
//        outputFileGroup=${outputFileGroup-$myGroup}

    println("Exited script ${WRAPPED_SCRIPT} with value ${exitCode}")

    Thread.sleep(2000);
    return exitCode;
}

void performAutocleanup() {
    //        # Set this in your command factory class, when roddy should clean up the dir for you.
//        [[ ${RODDY_AUTOCLEANUP_SCRATCH-false} == "true" ]] && rm -rf ${RODDY_SCRATCH} && echo "Auto cleaned up RODDY_SCRATCH"

}

void finalChecksAndExit(int code) {
    if (code == 0) System.exit(0);
    if (code == 100) {
        println "Finished script with 99 for compatibility reasons with Sun Grid Engine. 100 is reserved for SGE usage."
        System.exit 99
    }
    System.exit(code);
}

// Perform initial tests
checkWrapperScriptAndAbortIfNecessary()
checkParentJobsAndAbortIfNecessary()

// Setup environment
checkAndSetLockCommand()
resetLibraryPath()
checkAndSetJobVariables()

// Run the job
appendToJobStateLogFile("STARTED");

createJobDirectories()
int exitcode = runWrappedScript();

appendToJobStateLogFile("" + exitcode);

// Finalize
performAutocleanup()
finalChecksAndExit(exitcode)
