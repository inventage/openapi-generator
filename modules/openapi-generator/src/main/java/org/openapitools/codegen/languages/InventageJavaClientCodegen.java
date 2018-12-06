package org.openapitools.codegen.languages;

import static org.openapitools.codegen.utils.GeneratorUtils.groupOperationsIntoSingleGroup;

import java.util.List;
import java.util.Map;

import org.openapitools.codegen.CodegenOperation;

import io.swagger.v3.oas.models.OpenAPI;

/**
 * Generates an interface usable by the RESTeasy client proxy framework.
 *
 * @author Simon Marti
 */
public class InventageJavaClientCodegen extends InventageJavaServerCodegen {

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "inventage-java-client";
    }


    /** {@inheritDoc} */
    @Override
    public void processOpts() {
        super.processOpts();

        apiTemplateFiles.clear();

        apiTemplateFiles.put("clientInterface.mustache", ".java");
    }

    /** {@inheritDoc} */
    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);

        supportingFiles.clear();
    }

    @Override
    public void addOperationToGroup(String tag, String resourcePath, io.swagger.v3.oas.models.Operation operation, CodegenOperation codegenOperation, Map<String, List<CodegenOperation>> operationGroups) {
        groupOperationsIntoSingleGroup(tag, resourcePath, operation, codegenOperation, operationGroups);
    }

    /** {@inheritDoc} */
    @Override
    public String toApiName(final String name) {
        return shortAppName + "Client";
    }

    /** {@inheritDoc} */
    @Override
    public String getLibrary() {
        return JAX_RS;
    }

    /** {@inheritDoc} */
    @Override
    public String toBooleanGetter(final String name) {
        return super.toGetter(name);
    }

}
