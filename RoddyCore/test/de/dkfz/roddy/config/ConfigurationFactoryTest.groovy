package de.dkfz.roddy.config

import org.junit.BeforeClass
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder

/**
 * Tests for ConfigurationFactory
 */
public class ConfigurationFactoryTest {

    public static TemporaryFolder tempFolder = new TemporaryFolder();

    private static File testFolder1

    private static File testFolder2

    private static File testFolder3

    private static File testFolder4

    @BeforeClass
    public static void setupClass() {

        // Create buggy directories first.
        tempFolder.create();

        testFolder1 = tempFolder.newFolder("normalFolder1");
        testFolder2 = tempFolder.newFolder("normalFolder2");
        testFolder3 = tempFolder.newFolder("buggyCfgFolder1");
        testFolder4 = tempFolder.newFolder("buggyCfgFolder2");


        Map<String, String> configurationFiles = [
                "project_A"  : testFolder1,
                "project_B"  : testFolder1,
                "project_C"  : testFolder1,
                "project_D"  : testFolder2,
                "project_E"  : testFolder2,
                "project_F"  : testFolder2,
                "something_A": testFolder1,
                "something_B": testFolder2,

                "project_G"  : testFolder3,
                "project_H"  : testFolder3,
                "project_I"  : testFolder3,
                "project_J"  : testFolder4,
                "project_K"  : testFolder4,
                "project_L"  : testFolder4,
                "something_C": testFolder3,
                "something_D": testFolder4,
        ]

        configurationFiles.each {
            String k, File f ->
                if (k.startsWith("project")) {
                    new File(f, "${k}.xml") << "<configuration configurationType='project' name='project_${k}'></configuration>";
                } else if (k.startsWith("something")) {
                    new File(f, "${k}.xml") << "<configuration name='standard_${k}'></configuration>";
                }
        }
        testFolder3.setReadable(false);
        testFolder4.setReadable(true, true);
    }

    @Test
    public void testLoadInvalidConfigurationDirectories() {
        // Load context from invalid directories and see, if the step fails.
        ConfigurationFactory.initialize([testFolder3, testFolder4])

        testFolder3.setReadable(true);
        testFolder4.setReadable(true);

        assert ConfigurationFactory.getInstance().getAvailableProjectConfigurations().size() == 3;
        assert ConfigurationFactory.getInstance().getAvailableConfigurationsOfType(Configuration.ConfigurationType.OTHER).size() == 1;
    }

    @Test
    public void testLoadValidConfigurationDirectories() {
        // Load context from valid directories and see, if the step fails.
        ConfigurationFactory.initialize([testFolder1, testFolder2])

        assert ConfigurationFactory.getInstance().getAvailableProjectConfigurations().size() == 6;
        assert ConfigurationFactory.getInstance().getAvailableConfigurationsOfType(Configuration.ConfigurationType.OTHER).size() == 2;
    }
}
