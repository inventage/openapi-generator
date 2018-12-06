package org.openapitools.codegen.utils;

import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.openapitools.codegen.languages.InventageJavaServerCodegen.SERVICE_NAME;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openapitools.codegen.CodegenOperation;

import io.swagger.models.Operation;
import io.swagger.v3.oas.models.OpenAPI;

/**
 * Commonly used helper methods for code generation.
 *
 * @author Simon Marti
 */
public final class GeneratorUtils {

    //---- Static

    private static final String SHORT_APP_NAME = "shortAppName";
    private static final String SERVICE_ENDPOINT_NAME = "serviceEndpointName";
    private static final Pattern BASE_PATH_PATTERN = Pattern.compile("^/*([^/]+)(/.*|$)");

    /**
     * Groups operations by their base path, i.e. the first path segment in their respective URL.
     *
     * <p>For example: The resources /employees, /employees/{id} and /offices would be grouped into two API classes, EmployeesApi and OfficesApi.
     *
     * @param tag tag for operation
     * @param resourcePath full URL for operation
     * @param operation operation Swagger definition
     * @param codegenOperation operation data used for code generation
     * @param operationGroups existing groups of operations (will be modified by this method)
     */
    public static void groupOperationsByBasePath(String tag, String resourcePath, Operation operation, CodegenOperation codegenOperation,
                                                 Map<String, List<CodegenOperation>> operationGroups) {
        final Matcher basePathMatch = BASE_PATH_PATTERN.matcher(resourcePath);
        final String basePath;

        if (!basePathMatch.matches()) {
            basePath = "default";
        }
        else {
            basePath = basePathMatch.group(1).replaceAll("[^a-zA-Z]", "");
        }

        final List<CodegenOperation> operations = operationGroups.computeIfAbsent(basePath, key -> new ArrayList<>());
        if (operations.stream().anyMatch(cdgnOperation -> cdgnOperation.operationId.equals(codegenOperation.operationId))) {
            // This method is called multiple times for every tag of the operation, so we're ignoring everything but the first call.
            return;
        }

        operations.add(codegenOperation);

        codegenOperation.subresourceOperation = !codegenOperation.path.isEmpty();
        codegenOperation.baseName = basePath;
    }

    /**
     * Groups operations by their client group (vendor extension: 'x-client-group') or base path (if the client group is not available).
     *
     * @param tag tag for operation
     * @param resourcePath full URL for operation
     * @param operation operation Swagger definition
     * @param codegenOperation operation data used for code generation
     * @param operationGroups existing groups of operations (will be modified by this method)
     */
    public static void groupOperationsClientGroup(String tag, String resourcePath, Operation operation, CodegenOperation codegenOperation,
                                                  Map<String, List<CodegenOperation>> operationGroups) {
        final Matcher basePathMatch = BASE_PATH_PATTERN.matcher(resourcePath);
        final String basePath;

        if (!basePathMatch.matches()) {
            basePath = "default";
        }
        else {
            basePath = basePathMatch.group(1).replaceAll("[^a-zA-Z]", "");
        }

        String clientGroupKey = basePath;
        if (codegenOperation.vendorExtensions.containsKey("x-client-group")) {
            clientGroupKey = (String) codegenOperation.vendorExtensions.get("x-client-group");
        }

        final List<CodegenOperation> operations = operationGroups.computeIfAbsent(clientGroupKey, key -> new ArrayList<>());
        if (operations.stream().anyMatch(cdgnOperation -> cdgnOperation.operationId.equals(codegenOperation.operationId))) {
            // This method is called multiple times for every tag of the operation, so we're ignoring everything but the first call.
            return;
        }

        operations.add(codegenOperation);

        codegenOperation.subresourceOperation = !codegenOperation.path.isEmpty();
        codegenOperation.baseName = basePath;
    }

    /**
     * Don't group operations, create separate API classes for every operation.
     *
     * @param tag tag for operation
     * @param resourcePath full URL for operation
     * @param operation operation Swagger definition
     * @param codegenOperation operation data used for code generation
     * @param operationGroups existing groups of operations (will be modified by this method)
     */
    public static void groupOperationsByOperationId(String tag, String resourcePath, Operation operation, CodegenOperation codegenOperation,
                                                    Map<String, List<CodegenOperation>> operationGroups) {

        operationGroups.computeIfAbsent(codegenOperation.operationId, key -> new ArrayList<>()).add(codegenOperation);
    }

    public static void groupOperationsByOperationId(String tag, String resourcePath, io.swagger.v3.oas.models.Operation operation, CodegenOperation codegenOperation,
                                                    Map<String, List<CodegenOperation>> operationGroups) {

        operationGroups.computeIfAbsent(codegenOperation.operationId, key -> new ArrayList<>()).add(codegenOperation);
    }

    /**
     * Group all operations into a single group named {@code "global"}.
     *
     * @param tag tag for operation
     * @param resourcePath full URL for operation
     * @param operation operation Swagger definition
     * @param codegenOperation operation data used for code generation
     * @param operationGroups existing groups of operations (will be modified by this method)
     */
    public static void groupOperationsIntoSingleGroup(String tag, String resourcePath, io.swagger.v3.oas.models.Operation operation, CodegenOperation codegenOperation,
                                                      Map<String, List<CodegenOperation>> operationGroups) {

        operationGroups.computeIfAbsent("global", key -> new ArrayList<>()).add(codegenOperation);
    }

    /**
     * Returns path to the .swagger-codegen-ignore file.
     *
     * @param outputFolder output folder of the Swagger code generator
     * @return path to the .swagger-codegen-ignore file
     */
    public static File ignoreFile(String outputFolder) {
        final String ignoreFileNameTarget = outputFolder + File.separator + ".swagger-codegen-ignore";
        return new File(ignoreFileNameTarget);
    }

    /**
     * Returns the given string with all whitespace stripped, every letter following whitespace in uppercase and every other letter in lowercase.
     *
     * <p>Example: {@code "Some Sample REST Application"} becomes {@code "someSampleRestApplication"}</p>
     *
     * @param string string to transform
     * @return {@code string} in camel-case
     */
    public static String camelizeSpacedString(String string) {
        final String strippedName = string.toLowerCase(Locale.US).replaceAll("[^a-zA-Z ]", "");
        final Matcher matcher = Pattern.compile("\\s+(\\w)").matcher(strippedName);

        final StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, matcher.group(1).toUpperCase(Locale.US));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Extracts the short app name from the swagger definition or the additional properties and stores it as an additional property.
     *
     * <p>The following definitions are checked in order and the first valid one is returned:</p>
     * <ul>
     *     <li>{@code serviceName} property</li>
     *     <li>{@code x-short-name} attribute in the Swagger definition's {@code info} section</li>
     *     <li>{@code title} attribute in the Swagger definition's {@code info} section</li>
     * </ul>
     *
     * @param additionalProperties additional properties of the swagger code generator
     * @param openAPI the openAPI definition
     * @return the short app name
     */
    public static String extractShortAppName(Map<String, Object> additionalProperties, OpenAPI openAPI) {
        final Map<String, Object> extensions = openAPI.getInfo().getExtensions();
        final Object shortName = extensions != null ? extensions.get("x-short-name") : null;
        final Object configuredShortAppName = additionalProperties.getOrDefault(SERVICE_NAME, shortName);

        final String serviceEndpointName;
        if (configuredShortAppName instanceof String) {
            serviceEndpointName = camelizeSpacedString((String) configuredShortAppName);
        }
        else {
            serviceEndpointName = camelizeSpacedString(openAPI.getInfo().getTitle());
        }
        final String shortAppName = capitalize(serviceEndpointName);
        additionalProperties.put(SHORT_APP_NAME, shortAppName);
        additionalProperties.put(SERVICE_ENDPOINT_NAME, serviceEndpointName);
        return shortAppName;
    }

    /**
     * Returns the given string in uppercase, with underscores before every capital letter of the original string.
     *
     * <p>Example: {@code "partnerId"} becomes {@code "PARTNER_ID"}</p>
     *
     * @param string string to transform
     * @return {@code string} in uppercase
     */
    public static String constantName(String string) {
        final Matcher matcher = Pattern.compile("[A-Z]").matcher(string);

        final StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, "_" + matcher.group(0));
        }
        matcher.appendTail(result);

        return result.toString().toUpperCase(Locale.US);
    }


    //---- Constructor

    private GeneratorUtils() {
        // nop
    }

}
