package org.openapitools.codegen.inventage;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.languages.InventageJavaServerCodegen;
import org.openapitools.codegen.templating.HandlebarsEngineAdapter;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;

/**
 * Tests for the {@link InventageJavaServerCodegen}. Options which are normally handed over by the maven-plugin-config in a pom file can be tested here.
 *
 * @author Tobias Gmünder
 */
public class InventageGeneratorTest {

    //---- Tests

    @Test
    @Ignore
    public void generateWithDefaults_shouldGenerateFiles() {
        final OpenAPI openAPI = new OpenAPIParser().readLocation("/3_0/inventageXEnumerations.yaml", null, new ParseOptions()).getOpenAPI();

        final ClientOptInput clientOptInput = new ClientOptInput();
        clientOptInput.setOpenAPI(openAPI);

        final InventageJavaServerCodegen codegenConfig = new InventageJavaServerCodegen();
        codegenConfig.setTemplatingEngine(new HandlebarsEngineAdapter());
        codegenConfig.setSerializableModel(true);


        final String outputDir = getTempDir();
        System.out.println(outputDir);
        codegenConfig.setOutputDir(outputDir);

        clientOptInput.setConfig(codegenConfig);

        new DefaultGenerator()
                .opts(clientOptInput)
                .generate();
    }

    private String getTempDir() {
        final String testOutputDir = System.getenv("TEST_OUTPUT_DIR");

        final Path outputPath = Paths.get(testOutputDir);
        try {
            Files.deleteIfExists(outputPath);
        }
        catch (DirectoryNotEmptyException e) {
            try {
                Files.walk(outputPath)
                        .sorted(Comparator.reverseOrder())
                .map(path -> path.toFile())
                .forEach(file -> file.delete());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Could not delete output folder");
        }
        try {
            Files.createDirectory(outputPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create output folder");
        }
        return testOutputDir;
    }
}
