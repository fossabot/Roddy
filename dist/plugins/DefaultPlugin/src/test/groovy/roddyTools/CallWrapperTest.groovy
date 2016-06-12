package roddyTools;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by heinold on 09.06.16.
 */
public class CallWrapperTest {


    private File createTempConfig(String entries, String method) {
        File file = File.createTempFile("roddyTest_CallWrapper", method);
        file.deleteOnExit()


        file << entries;
        file
    }

    @Test
    public void loadConfigurationValues() throws Exception {
        def s = "a=123\nb=456\nc=einwert"
        File file = createTempConfig(s, "loadConfigurationValues")

        CallWrapper callWrapper = new CallWrapper()
        callWrapper.cvalues = ["CONFIG_FILE": file.absolutePath]
        assert callWrapper.loadConfigurationValues() == [
                "a": "123",
                "b": "456",
                "c": "einwert"
        ]

    }

    @Test
    public void getEnvironmentVariables() throws Exception {
        CallWrapper callWrapper = new CallWrapper()
        def userID = System.getenv("user.name")
        def userHome = System.getenv("user.home")
        assert callWrapper.getEnvironmentVariables().keySet().containsAll(["HOME", "USER", "HOST", "SHELL"])
    }

    @Test
    public void checkAndSetJobVariablesWithPresetValues() throws Exception {
        CallWrapper callWrapper = new CallWrapper()
        callWrapper.cvalues = [
                "RODDY_JOBID"      : "ABCDEFG",
                "RODDY_SCRATCH"    : "/tmp",
                "defaultScratchDir": "/tmp/scrt"
        ]
        callWrapper.checkAndSetJobVariables()
        assert callWrapper.RODDY_JOBID == "ABCDEFG"
        assert callWrapper.RODDY_SCRATCH == "/tmp"
    }

    @Test
    public void checkAndSetJobVariablesWithStandardValues() throws Exception {
        CallWrapper callWrapper = new CallWrapper()
        callWrapper.cvalues = [:]
        callWrapper.checkAndSetJobVariables()
        assert callWrapper.RODDY_JOBID.isNumber()
        assert callWrapper.RODDY_SCRATCH == "/data/roddyScratch/${callWrapper.RODDY_JOBID}"
    }

    @Test(expected = RuntimeException)
    public void fillVariablesWithMissingEntries() throws Exception {
        CallWrapper callWrapper = new CallWrapper();
        callWrapper.fillVariables();
    }

    @Test(expected = RuntimeException)
    public void fillVariablesWithSomeMissingEntries() throws Exception {
        def s = ["TOOL_ID=abcd"].join("\n")
        CallWrapper callWrapper = new CallWrapper();
        callWrapper.fakeEnvVariablesForTests = ["CONFIG_FILE": createTempConfig(s, "loadConfigurationValues").absolutePath]
        callWrapper.fillVariables();
    }

    public void fillVariables() throws Exception {
        CallWrapper callWrapper = new CallWrapper();
        callWrapper.fillVariables();
    }

    @Test
    public void exec() throws Exception {

    }

    @Test
    public void exec1() throws Exception {

    }

    @Test
    public void checkParentJobsAndAbortIfNecessary() throws Exception {

    }

    @Test
    public void checkAndSetLockCommand() throws Exception {

    }

    @Test
    public void resetLibraryPath() throws Exception {

    }

    @Test
    public void createJobDirectories() throws Exception {

    }

    @Test
    public void createExportsForEnvironmentVariables() throws Exception {

    }

    @Test
    public void appendToJobStateLogFile() throws Exception {

    }

    @Test
    public void runWrappedScript() throws Exception {

    }

    @Test
    public void performAutocleanup() throws Exception {

    }

    @Test
    public void performFinalChecks() throws Exception {

    }

    @Test
    public void run() throws Exception {

    }

}