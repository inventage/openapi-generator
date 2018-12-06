package org.openapitools.codegen.inventage;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.ClientOpts;
import org.openapitools.codegen.DefaultCodegen;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.languages.InventageJavaServerCodegen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    //---- Static

    private static final Logger LOG = LoggerFactory.getLogger(InventageGeneratorTest.class);


    //---- Tests

    @Test
    public void generateWithDefaults_shouldGenerateFiles() {
        final OpenAPI openAPI = new OpenAPIParser().readLocation("/3_0/petstore.yaml", null, new ParseOptions()).getOpenAPI();

        final ClientOptInput clientOptInput = new ClientOptInput();
        clientOptInput.setOpenAPI(openAPI);

        final DefaultCodegen config = new InventageJavaServerCodegen();

        final String outputDir = getTempDir();
        LOG.info("Using {} for generated files", outputDir);
        config.setOutputDir(outputDir);

        clientOptInput.setConfig(config);
        clientOptInput.setOpts(new ClientOpts());

        final List<File> generatedFiles = new DefaultGenerator()
                .opts(clientOptInput)
                .generate();

        assertThat(generatedFiles, not(empty()));
    }

    private String getTempDir() {
        try {
            final Path tempDirectory = Files.createTempDirectory("openapi-generator-");
            return tempDirectory.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
