package de.dkfz.roddy.knowledge.brawlworkflows

import de.dkfz.roddy.config.AnalysisConfiguration;
import de.dkfz.roddy.config.ContextConfiguration
import de.dkfz.roddy.config.ProjectConfiguration
import de.dkfz.roddy.config.ToolEntry;
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileGroup
import de.dkfz.roddy.knowledge.files.GenericFileGroup
import groovy.transform.TypeCheckingMode;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;

import static org.junit.Assert.*;

/**
 * Created by heinold on 18.11.15.
 */
@groovy.transform.CompileStatic
public class BrawlWorkflowTest {


    public static final String LOAD_FASTQ_FILES = "loadFastqFiles"

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private String callPrepareAndFormatLine(String line) {
        Method prepareAndReformatLine = BrawlWorkflow.getDeclaredMethod("prepareAndReformatLine", String);
        prepareAndReformatLine.setAccessible(true);
        return prepareAndReformatLine.invoke(null, line)
    }

    @Test
    public void testPrepareAndReformatLine() {

        def linesAndExpected = [
                "set  srs=call context.getRuntimeService ( );": "set srs = call context.getRuntimeService ( );",
                "if(a==b())"                                  : "if ( a == b ( ) )",
                'if !runIndelDeepAnnotation ; then'           : "if ! runIndelDeepAnnotation; then",
                'set deepAnnotatedVCFFile=call "indelDeepAnnotation" (bamTumorMerged, bamControlMerged, rawVCFFile, "PIPENAME=INDEL_DEEPANNOTATION")'
                                                              : 'set deepAnnotatedVCFFile = call "indelDeepAnnotation" ( bamTumorMerged, bamControlMerged, rawVCFFile, "PIPENAME=INDEL_DEEPANNOTATION" )'

        ]

        linesAndExpected.each {
            String line, String exp ->
                assert callPrepareAndFormatLine(line) == exp;
        }
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private String callAssembleCall(String[] _l, int indexOfCallee, StringBuilder temp, ContextConfiguration configuration, LinkedHashMap<String, String> knownObjects) {
        Method assembleCall = BrawlWorkflow.class.getDeclaredMethod("_assembleCall", String[], int, StringBuilder, ContextConfiguration, LinkedHashMap);
        assembleCall.setAccessible(true);

        return (String)assembleCall.invoke(null, _l, indexOfCallee, temp, configuration, knownObjects);
    }

    @Test
    public void testAssembleCall() {
        callAssembleCall(null, 0, null, null, null);
    }

    @groovy.transform.CompileStatic(TypeCheckingMode.SKIP)
    private String callAssembleLoadFilesCall(String[] _l, int indexOfCallee, StringBuilder temp, ContextConfiguration configuration, LinkedHashMap<String, String> knownObjects) {
        Method assembleLoadFilesCall = BrawlWorkflow.class.getDeclaredMethod("_assembleLoadFilesCall", String[], int, StringBuilder, ContextConfiguration, LinkedHashMap);
        assembleLoadFilesCall.setAccessible(true);
        return (String)assembleLoadFilesCall.invoke(null, _l, indexOfCallee, temp, configuration, knownObjects);
    }

    static class TestFile extends BaseFile {
        TestFile(BaseFile parentFile) {
            super(parentFile)
        }
    }

    @Test
    public void testAssembleLoadFilesCall() {
        StringBuilder tempBuilder = new StringBuilder();

        def aCfg = new AnalysisConfiguration(null, null, null, null, null, null, null)
        def pCfg = new ProjectConfiguration(null, null, null, null)
        ContextConfiguration cc = new ContextConfiguration(aCfg, pCfg);

        def loadFastqFiles = new ToolEntry(LOAD_FASTQ_FILES, "testtools", "/tmp/testtools/${LOAD_FASTQ_FILES}.sh")

        loadFastqFiles.getOutputParameters(cc).add(new ToolEntry.ToolFileGroupParameter(GenericFileGroup.getClass() as Class<FileGroup>, [new ToolEntry.ToolFileParameter(TestFile.class as Class<BaseFile>, new LinkedList<ToolEntry.ToolConstraint>(), "FUZZY", true)], "FUZZY_GROUP", ToolEntry.ToolFileGroupParameter.PassOptions.parameters));
        cc.getTools().add(loadFastqFiles)

        String[] _l = callPrepareAndFormatLine("""set inputfiles = loadfilesWith "${LOAD_FASTQ_FILES}"()'""").split("[ ]")
        int indexOfCallee = 4;

        def expected = """ = de.dkfz.roddy.knowledge.files.GenericFileGroup<TestFile> inputfiles = new de.dkfz.roddy.knowledge.files.GenericFileGroup(ExecutionService.getInstance().executeTool(context, ${LOAD_FASTQ_FILES}.replaceAll('"', "")).collect { it -> new TestFile(it) });"""
        def foundClass = callAssembleLoadFilesCall(_l, indexOfCallee, tempBuilder, cc, null);
        assert foundClass == "de.dkfz.roddy.knowledge.files.GenericFileGroup<TestFile>"
        assert expected == tempBuilder.toString();
    }

}