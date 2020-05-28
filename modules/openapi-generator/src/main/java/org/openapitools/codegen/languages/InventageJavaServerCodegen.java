package org.openapitools.codegen.languages;

import com.google.common.collect.ImmutableMap;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.CodegenResponse;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.templating.HandlebarsEngineAdapter;
import org.openapitools.codegen.utils.GeneratorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.lang.Boolean.TRUE;
import static java.lang.Character.isUpperCase;
import static java.lang.Integer.parseInt;
import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Locale.ENGLISH;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.openapitools.codegen.utils.GeneratorUtils.constantName;
import static org.openapitools.codegen.utils.GeneratorUtils.groupOperationsByOperationId;
import static org.openapitools.codegen.utils.ModelUtils.isArraySchema;
import static org.openapitools.codegen.utils.ModelUtils.isStringSchema;
import static org.openapitools.codegen.utils.StringUtils.camelize;


/**
 * Generates a JAX-RS or Spring server stub.
 *
 * @author Simon Marti
 * @author Tobias Gmünder
 */
@SuppressWarnings("Duplicates")
public class InventageJavaServerCodegen extends AbstractJavaJAXRSServerCodegen {

    //---- Static

    /**
     * The name of the additional property which controls the generation of XML annotations (jackson/jaxb).
     */
    private static final String ADD_PROP_GENERATE_XML = "generateXMLAnnotations";
    /**
     * The name of the additional property which controls the generation of Json annotations (jackson/json).
     */
    private static final String ADD_PROP_GENERATE_JSON = "generateJsonAnnotations";

    private static final Logger LOG = LoggerFactory.getLogger(InventageJavaServerCodegen.class);

    protected static final String JAX_RS = "jax-rs";
    protected static final String SPRING = "spring";

    // Inventage Vendor Extensions (IVE)

    /**
     * Name of vendor extension to indicate an 'enum' with no compile time dependency to the whole value set is desired.
     */
    private static final String IVE_XENUMERATION = "x-enumeration";

    /**
     * Name of the the vendor extension used to indicate the Java type of an 'x-enumeration'. Only used internally in the generator.
     */
    private static final String IVE_XENUMERATION_TYPE = "x-enumeration-type";

    /**
     * The name of the vendor extension used to indicate there should be a Java type generated which wrappes a single property value.
     */
    private static final String IVE_XWRAPPER = "x-wrapper";


    protected static final Map<String, String> ACCENT_REPLACEMENTS = ImmutableMap.<String, String>builder()
        .put("ä", "ae")
        .put("ö", "oe")
        .put("ü", "ue")
        .put("Ä", "Ae")
        .put("Ö", "Oe")
        .put("Ü", "Ue")
        .build();

    private static final String OPERATION_NAMING = "operationNaming";
    public static final String SERVICE_NAME = "serviceName";


    //---- Constructor

    /**
     * Creates a new instance.
     */
    public InventageJavaServerCodegen() {
        super();

        setTemplatingEngine(new HandlebarsEngineAdapter());

        outputFolder = "generated-code" + File.separator + "java";
        templateDir = "templates" + File.separator + "InventageJava";
        embeddedTemplateDir = templateDir;
        invokerPackage = "com.inventage.example.client";
        artifactId = "inventage-java-api";
        apiPackage = "com.inventage.example.api";
        modelPackage = "com.inventage.example.model";

        supportedLibraries.put(JAX_RS, "Java EE 7 JAX-RS Server stub");
        supportedLibraries.put(SPRING, "Spring Server stub");

        final CliOption libraryOption = new CliOption(CodegenConstants.LIBRARY, "library template (sub-template) to use");
        libraryOption.setEnum(supportedLibraries);
        libraryOption.setDefault(JAX_RS);
        cliOptions.add(libraryOption);

        cliOptions.add(CliOption.newString(OPERATION_NAMING, "Naming strategy for operations")
            .addEnum(NamingStrategy.AUTO.name(), "Automatically chose a strategy based on the available information")
            .addEnum(NamingStrategy.PATH.name(), "Build a name based on the operation's path")
            .defaultValue(NamingStrategy.AUTO.name()));

        cliOptions.add(CliOption.newBoolean(SERVICE_NAME, "Custom API name used for class names"));
    }


    //---- Fields

    protected String shortAppName;
    protected NamingStrategy operationNaming;


    //---- Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "inventage-java";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHelp() {
        return "Generates a JAX-RS server stub.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processOpts() {
        super.processOpts();

        supportingFiles.clear();
        modelDocTemplateFiles.clear();
        apiDocTemplateFiles.clear();
        apiTemplateFiles.clear();
        apiTestTemplateFiles.clear();

        if (JAX_RS.equals(getLibrary())) {
            additionalProperties.put("jaxrs", "true");
        }
        else if (SPRING.equals(getLibrary())) {
            additionalProperties.put("spring", "true");
        }

        operationNaming = NamingStrategy.from(additionalProperties.get("operationNaming"));

        apiTemplateFiles.put("interface.mustache", ".java");
        apiTemplateFiles.put("adapter.mustache", "Adapter.java");

        importMapping.put("DecimalMax", "javax.validation.constraints.DecimalMax");
        importMapping.put("DecimalMin", "javax.validation.constraints.DecimalMin");
        importMapping.put("JsonInclude", "com.fasterxml.jackson.annotation.JsonInclude");
        importMapping.put("JsonInclude.Include", "com.fasterxml.jackson.annotation.JsonInclude.Include");
        importMapping.put("JsonFormat", "com.fasterxml.jackson.annotation.JsonFormat");
        importMapping.put("JsonValue", "com.fasterxml.jackson.annotation.JsonValue");
        importMapping.put("JsonCreator", "com.fasterxml.jackson.annotation.JsonCreator");
        importMapping.put("Pattern", "javax.validation.constraints.Pattern");
        importMapping.put("NotNull", "javax.validation.constraints.NotNull");
        importMapping.put("Max", "javax.validation.constraints.Max");
        importMapping.put("Min", "javax.validation.constraints.Min");
        importMapping.put("Size", "javax.validation.constraints.Size");
        importMapping.put("Valid", "javax.validation.Valid");
        importMapping.put("Serializable", "java.io.Serializable");
        importMapping.put("EqualsBuilder", "org.apache.commons.lang3.builder.EqualsBuilder");
        importMapping.put("ToStringBuilder", "org.apache.commons.lang3.builder.ToStringBuilder");
        importMapping.put("HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder");
        importMapping.put("Locale", "java.util.Locale");
        importMapping.put("Optional", "java.util.Optional");
        importMapping.put("Collectors", "java.util.stream.Collectors");
        importMapping.put("ArrayList", "java.util.ArrayList");
        importMapping.put("OffsetDateTime", "java.time.OffsetDateTime");
        importMapping.put("PathSegment", "javax.ws.rs.core.PathSegment");
        importMapping.put("GET", "javax.ws.rs.GET");
        importMapping.put("POST", "javax.ws.rs.POST");
        importMapping.put("PUT", "javax.ws.rs.PUT");
        importMapping.put("DELETE", "javax.ws.rs.DELETE");
        importMapping.put("Map", "java.util.Map");
        importMapping.remove("com.fasterxml.jackson.annotation.JsonProperty");

        // XML
        importMapping.put("XmlRootElement", "javax.xml.bind.annotation.XmlRootElement");
        importMapping.put("XmlAccessorType", "javax.xml.bind.annotation.XmlAccessorType");
        importMapping.put("XmlAccessType", "javax.xml.bind.annotation.XmlAccessType");
        importMapping.put("JacksonXmlProperty", "com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty");
        importMapping.put("XmlElement", "javax.xml.bind.annotation.XmlElement");
        importMapping.put("XmlEnum", "javax.xml.bind.annotation.XmlEnum");
        importMapping.put("XmlEnumValue", "javax.xml.bind.annotation.XmlEnumValue");
        importMapping.put("XmlJavaTypeAdapter", "javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter");
        importMapping.put("XmlAttribute", "javax.xml.bind.annotation.XmlAttribute");
        importMapping.put("OffsetDateTimeXmlAdapter", "com.migesok.jaxb.adapter.javatime.OffsetDateTimeXmlAdapter");
        importMapping.put("JacksonXmlElementWrapper", "com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper");
        importMapping.put("XmlElementWrapper", "javax.xml.bind.annotation.XmlElementWrapper");
    }

    @Override
    public CodegenProperty fromProperty(String name, Schema p) {
        final CodegenProperty codegenProperty = super.fromProperty(name, p);

        if (isArraySchema(p)
            && codegenProperty.items.getVendorExtensions() != null
            && codegenProperty.items.getVendorExtensions().containsKey(IVE_XWRAPPER)
        ) {
            codegenProperty.dataType = codegenProperty.baseType + "<" + codegenProperty.complexType + ">";
            codegenProperty.datatypeWithEnum = codegenProperty.dataType;
        }

        if (isXEnumeration(p)) {
            codegenProperty.openApiType = getXEnumerationType(p);
            codegenProperty.dataType = getXEnumerationType(p);
            codegenProperty.datatypeWithEnum = getXEnumerationType(p);
            codegenProperty.baseType = getXEnumerationType(p);

            if (isStringSchema(p)) {
                codegenProperty.isString = false;
                codegenProperty.minimum = null;
                codegenProperty.minLength = null;
                codegenProperty.maximum = null;
                codegenProperty.maxLength = null;
            }
        }

        getExtension(IVE_XWRAPPER, p).ifPresent(extensionValue -> {
            codegenProperty.openApiType = getXEnumerationType(p);
            codegenProperty.dataType = getXEnumerationType(p);
            codegenProperty.datatypeWithEnum = getXEnumerationType(p);
            codegenProperty.baseType = getXEnumerationType(p);
        });

        return codegenProperty;
    }

    @Override
    protected void setNonArrayMapProperty(CodegenProperty property, String type) {
        super.setNonArrayMapProperty(property, type);

        if (isXEnumeration(property)) {
            property.isPrimitiveType = false;
        }
        getExtension(IVE_XWRAPPER, property).ifPresent(extensionValue -> property.isPrimitiveType = false);
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);

        shortAppName = GeneratorUtils.extractShortAppName(additionalProperties, openAPI);

        final String apiFolder = sourceFolder + File.separator + apiPackage.replace('.', '/');
        if (JAX_RS.equals(getLibrary())) {
            supportingFiles.add(
                new SupportingFile("application.hbs",
                    apiFolder,
                    format(ENGLISH, "Abstract%sApplication.java", shortAppName)));
        }

        additionalProperties.put("swaggerFileApplication", TRUE);

        // add full swagger definition in a mustache parameter
        final String swaggerDef = Json.pretty(openAPI);
        this.additionalProperties.put("fullSwagger", swaggerDef);

        // Reorder schemas: move x-enumerations 1st as otherwise the resolving/inlining for properties won't work (no type generated).
        // This is not a fix but a workaround.
        if (openAPI.getComponents().getSchemas() instanceof LinkedHashMap) {
            LOG.info("Ordered map found: reordering to move x-enumerations before all other object definitions");
            final LinkedHashMap<String, Schema> schemas = (LinkedHashMap<String, Schema>) openAPI.getComponents().getSchemas();

            final Map<String, Schema> sortedMap = schemas.entrySet().stream().sorted(new Comparator<Map.Entry<String, Schema>>() {
                @Override
                public int compare(Map.Entry<String, Schema> o1, Map.Entry<String, Schema> o2) {
                    final boolean schema1Enum = isXEnumeration(o1.getValue());
                    final boolean schema2Enum = isXEnumeration(o2.getValue());

                    if ((schema1Enum && schema2Enum) || (!schema1Enum && !schema2Enum)) { // NOPMD
                        // both x-enums or none, we don't care about the order
                        return 0;
                    }
                    if (schema1Enum) {
                        return -1;
                    }
                    else {
                        return 1;
                    }
                }
            }).collect(toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (u, v) -> {
                    throw new IllegalStateException(format(ENGLISH, "Duplicate key %s", u));
                },
                LinkedHashMap::new)
            );
            openAPI.getComponents().setSchemas(sortedMap);
        }
    }


    @Override
    public CodegenModel fromModel(String name, Schema model) {
        final CodegenModel codegenModel = super.fromModel(name, model);

        if (isXEnumeration(model)) {
            model.getExtensions().put(IVE_XENUMERATION_TYPE, name);
        }
        getExtension(IVE_XWRAPPER, model).ifPresent(extensionValue -> {
            model.getExtensions().put(IVE_XENUMERATION_TYPE, name);

            // Manually create and attache a string property and attach it to the model (otherwise the generated model has
            // no 'vars' and hence no possibility to add constraints'
            final CodegenProperty cp = new CodegenProperty();
            cp.baseName = "value";
            cp.maxLength = model.getMaxLength();
            codegenModel.vars.add(cp);
            postProcessModelProperty(codegenModel, cp);

        });

        codegenModel.imports.remove("ApiModelProperty");
        codegenModel.imports.remove("ApiModel");
        if (codegenModel.dataType != null
            && (codegenModel.vendorExtensions.containsKey("x-enumeration") || codegenModel.vendorExtensions.containsKey(IVE_XWRAPPER))
        ) {
            if (generateJsonAnnotations()) {
                codegenModel.imports.add("JsonCreator");
                codegenModel.imports.add("JsonValue");
            }

            codegenModel.imports.add("Locale");

            // String definitions usually don't get generated because they're considered an "alias" to the String class
            codegenModel.isAlias = false;
        }
        else if (!codegenModel.isEnum) {
            //final String lib = getLibrary();
            //Needed imports for Jackson based libraries
            if (generateJsonAnnotations()) {
                codegenModel.imports.add("JsonProperty");
                codegenModel.imports.add("JsonInclude");
                codegenModel.imports.add("JsonInclude.Include");
            }
            if (generateXMLAnnotations()) {
                codegenModel.imports.add("XmlRootElement");
                codegenModel.imports.add("XmlAccessType");
                codegenModel.imports.add("XmlAccessorType");
                codegenModel.imports.add("XmlElement");
                codegenModel.imports.add("XmlJavaTypeAdapter");
                codegenModel.imports.add("JacksonXmlProperty");
                codegenModel.imports.add("OffsetDateTimeXmlAdapter");

                final long numberOfListContainerProperties = codegenModel.getVars().stream()
                    .filter(codegenProperty -> codegenProperty.isListContainer)
                    .count();

                if (numberOfListContainerProperties != 0) {
                    codegenModel.imports.add("XmlElementWrapper");
                    codegenModel.imports.add("JacksonXmlElementWrapper");
                }
            }
            if (additionalProperties.containsKey("gson")) {
                codegenModel.imports.add("SerializedName");
            }

            codegenModel.vendorExtensions.put("hashCodeInitial", abs(codegenModel.name.hashCode() % 57) * 2 + 1);
            codegenModel.vendorExtensions.put("hashCodeMultiplier", abs(codegenModel.classFilename.hashCode() % 61) * 2 + 1);
            codegenModel.imports.add("HashCodeBuilder");
            codegenModel.imports.add("EqualsBuilder");
            codegenModel.imports.add("ToStringBuilder");
            codegenModel.imports.add("Collectors");
            codegenModel.imports.add("Optional");
            codegenModel.imports.add("ArrayList");

            for (final CodegenProperty var : codegenModel.vars) {
                var.vendorExtensions.put("constantName", "PN_" + constantName(var.name));
                var.vendorExtensions.put("cloneable", cloneable(var));
                if (var.isListContainer) {
                    var.items.vendorExtensions.put("cloneable", cloneable(var.items));
                }
            }

            codegenModel.vendorExtensions.put("simple", codegenModel.vars.size() == 1);
        }
        else { // enum class
            if (generateJsonAnnotations()) {
                codegenModel.imports.add("JsonValue");
                codegenModel.imports.add("JsonCreator");
            }
            if (generateXMLAnnotations()) {
                codegenModel.imports.add("XmlEnumValue");
            }
        }

        if (hasInnerEnum(codegenModel)) {
            if (generateJsonAnnotations()) {
                codegenModel.imports.add("JsonValue");
                codegenModel.imports.add("JsonCreator");
            }
            if (generateXMLAnnotations()) {
                codegenModel.imports.add("XmlEnum");
                codegenModel.imports.add("XmlEnumValue");
            }
        }

        if (serializableModel) {
            codegenModel.imports.add("Serializable");
        }

        for (CodegenProperty cgp : codegenModel.vars) {
            if (cgp.vendorExtensions.containsKey("x-use-offset-date-time")) {
                updateCgP(cgp, codegenModel);
            }
        }

        return codegenModel;
    }

    private void updateCgP(CodegenProperty cgp, CodegenModel codegenModel) {
        LOG.debug("This model should use offsetDateTime: {}.{}", codegenModel.name, cgp.name);
        cgp.dataType = "OffsetDateTime";
        cgp.datatypeWithEnum = "OffsetDateTime";
    }

    private boolean cloneable(CodegenProperty property) {
        return !(property.isEnum || property.isPrimitiveType || importMapping.containsKey(property.dataType));
    }

    private boolean hasInnerEnum(CodegenModel codegenModel) {
        return codegenModel.vars.stream()
            .anyMatch(codegenProperty -> codegenProperty.isEnum);
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        final CodegenOperation codegenOperation = super.fromOperation(path, httpMethod, operation, servers);

        final Set<String> possibleReturnTypes = codegenOperation.responses.stream()
            .filter(response -> isSuccessfulResponse(response))
            .map(response -> response.code)
            .collect(toSet());
        codegenOperation.vendorExtensions.put("hasMultiple2xxReturnCodes", possibleReturnTypes.size() > 1);

        return codegenOperation;
    }

    @SuppressWarnings("MagicNumber")
    private static boolean isSuccessfulResponse(CodegenResponse response) {
        return parseInt(response.code) / 100 == 2;
    }

    @Override
    public void addOperationToGroup(String tag,
                                    String resourcePath,
                                    Operation operation,
                                    CodegenOperation codegenOperation,
                                    Map<String, List<CodegenOperation>> operationGroups
    ) {
        groupOperationsByOperationId(tag, resourcePath, operation, codegenOperation, operationGroups);
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);

        if (property.isDate || property.isDateTime) {
            model.imports.add("JsonFormat");

            if (dateLibrary.equals("java8-localdatetime")) {
                property.vendorExtensions.put("noTimeZone", true);
            }
        }


        if (property.pattern != null) {
            model.imports.add("Pattern");
        }

        final boolean isInt = property.isInteger || property.isLong;
        if (property.minimum != null) {
            model.imports.add(isInt ? "Min" : "DecimalMin");
            if (property.minimum.length() >= 10 && !property.minimum.contains(".")) {
                property.minimum += "L";
            }
        }
        if (property.maximum != null) {
            model.imports.add(isInt ? "Max" : "DecimalMax");
            if (property.maximum.length() >= 10 && !property.maximum.contains(".")) {
                property.maximum += "L";
            }
        }

        if (property.required) {
            model.imports.add("NotNull");
        }

        if (property.minLength != null || property.maxLength != null || property.minItems != null || property.maxItems != null) {
            model.imports.add("Size");
        }

        // Temporary fix for @Valid annotation: instead of all these exclusions, it would be nice to have a "isModelProperty"
        if (!property.isPrimitiveType && !property.isFloat && !property.isDate && !property.isDateTime) {
            model.imports.add("Valid");
        }

        if (property.vendorExtensions.containsKey("x-use-offset-date-time")) {
            model.imports.add("OffsetDateTime");
            property.dataType = "OffsetDateTime";
            property.datatypeWithEnum = "OffsetDateTime";
            property.baseType = "OffsetDateTime";
            LOG.debug("Contains key {}.{}: {}", model.name, property.name, property.datatypeWithEnum);
        }

        model.imports.remove("ApiModelProperty");
        model.imports.remove("ApiModel");
        model.imports.remove("JsonSerialize");
        model.imports.remove("ToStringSerializer");
        model.imports.remove("JsonValue");
        model.imports.remove("JsonProperty");
    }

    @Override
    protected String getOrGenerateOperationId(Operation operation, String path, String httpMethod) {
        if (operationNaming == NamingStrategy.PATH) {
            operation.setOperationId(null);
        }
        return super.getOrGenerateOperationId(operation, path, httpMethod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String sanitizeName(final String name) {
        String preSanitizedName = name;

        for (final Map.Entry<String, String> accentReplacement : ACCENT_REPLACEMENTS.entrySet()) {
            preSanitizedName = preSanitizedName.replace(accentReplacement.getKey(), accentReplacement.getValue());
        }

        preSanitizedName = StringUtils.stripAccents(preSanitizedName);

        return super.sanitizeName(preSanitizedName);
    }

    @Override
    public Map<String, Object> postProcessAllModels(Map<String, Object> objs) {
        final Map<String, Object> postProcessedModels = super.postProcessAllModels(objs);

        try {
            for (Map.Entry<String, Object> entry : postProcessedModels.entrySet()) {
                final Map<String, Object> singleModel = (Map<String, Object>) entry.getValue();
                final List<Map<String, Object>> models = (List<Map<String, Object>>) singleModel.get("models");

                for (Map<String, Object> model : models) {
                    final CodegenModel codegenModel = (CodegenModel) model.get("model");

                    if (codegenModel.getInterfaceModels() != null) {
                        for (CodegenModel interfaceModel : codegenModel.getInterfaceModels()) {
                            interfaceModel.getVendorExtensions().put("x-cdg-superclass", codegenModel.classname);
                            interfaceModel.setParent(codegenModel.classname);
                            // Don't set the the parentModel on the interfaceModel as this yields to an infinite loop
                        }
                        // Update the parent model
                        codegenModel.setVars(Collections.emptyList());
                        codegenModel.getVendorExtensions().put("x-cdg-no-builder", true);
                    }
                }
            }
        }
        catch (Exception e) {
            LOG.warn("Unable to set superclass property on child classes", e);
        }
        return postProcessedModels;
    }

    @Override
    public Map<String, Object> postProcessOperationsWithModels(Map<String, Object> objs, List<Object> allModels) {
        final Map<String, Object> newObjs = super.postProcessOperationsWithModels(objs, allModels);
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");

        if (operations != null) {
            final List<LinkedHashMap> imports = (List<LinkedHashMap>) newObjs.get("imports");
            final List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
            for (final CodegenOperation operation : ops) {
                LOG.info("Found: " + operation.httpMethod + " in " + operation);

                if (JAX_RS.equals(getLibrary())) {
                    addImport(operation.httpMethod.toUpperCase(Locale.US), imports);
                }
                importsForParamValidation(operation.pathParams, imports);
                importsForParamValidation(operation.queryParams, imports);
                importsForParamValidation(operation.bodyParams, imports);
                importsForParamValidation(operation.headerParams, imports);
            }
        }

        return newObjs;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getterAndSetterCapitalize(String name) {
        String localName = name;
        localName = toVarName(localName).codePoints()
            .filter(Character::isLetterOrDigit)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();

        if (localName.length() > 1 && isUpperCase(localName.charAt(1))) {
            return localName;
        }
        else {
            return camelize(localName);
        }
    }

    @Override
    public String toDefaultValue(Schema p) {
        if (isArraySchema(p)) {
            return "null";
        }
        final String defaultValue = super.toDefaultValue(p);
        return defaultValue == null ? "null" : defaultValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toApiName(String name) {
        final String apiName = super.toApiName(name);
        if (apiName.endsWith("Api")) {
            return apiName.substring(0, apiName.lastIndexOf("Api"));
        }
        return apiName;
        //return initialCaps(name);
    }

    private void importsForParamValidation(List<CodegenParameter> params, List<LinkedHashMap> imports) {
        for (final CodegenParameter param : params) {
            if (param.isEnum) {
                addImport("JsonValue", imports);
                addImport("JsonCreator", imports);
            }

            if (param.pattern != null) {
                addImport("Pattern", imports);
            }

            final boolean isInt = param.isInteger || param.isLong;
            if (param.minimum != null) {
                addImport(isInt ? "Min" : "DecimalMin", imports);
            }
            if (param.maximum != null) {
                addImport(isInt ? "Max" : "DecimalMax", imports);
            }

            if (param.required && !param.isBodyParam) {
                addImport("NotNull", imports);
            }

            if (param.minLength != null || param.maxLength != null || param.minItems != null || param.maxItems != null) {
                addImport("Size", imports);
            }

            if (param.isBodyParam) {
                addImport("Valid", imports);
                addImport("NotNull", imports);
            }
        }
    }

    private void addImport(String importName, List<LinkedHashMap> imports) {
        final String importClass = importMapping.get(importName);
        if (importClass != null && !hasImport(importClass, imports)) {
            final LinkedHashMap<String, String> newImport = new LinkedHashMap<>();
            newImport.put("import", importClass);
            imports.add(newImport);
        }
    }

    @SuppressWarnings("checkstyle:parametername")
    private boolean hasImport(String _import, List<LinkedHashMap> imports) {
        for (final LinkedHashMap map : imports) {
            if (map.containsValue(_import)) {
                return true;
            }
        }
        return false;
    }

    private boolean isXEnumeration(CodegenProperty codegenProperty) {
        if (codegenProperty == null) {
            return false;
        }
        return codegenProperty.getVendorExtensions() != null && codegenProperty.getVendorExtensions().containsKey(IVE_XENUMERATION);

    }

    private boolean isXEnumeration(Schema schema) {
        if (schema == null) {
            return false;
        }
        return schema.getExtensions() != null && schema.getExtensions().containsKey(IVE_XENUMERATION);
    }

    private String getXEnumerationType(Schema schema) {
        if (schema == null || schema.getExtensions() == null) {
            return null;
        }
        return (String) schema.getExtensions().get(IVE_XENUMERATION_TYPE);
    }

    private Optional<Object> getExtension(String extensionName, Schema schema) {
        if (schema == null || schema.getExtensions() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(schema.getExtensions().get(extensionName));
    }

    private Optional<Object> getExtension(String extensionName, CodegenProperty codegenProperty) {
        if (codegenProperty == null || codegenProperty.getVendorExtensions() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(codegenProperty.getVendorExtensions().get(extensionName));
    }

    private boolean generateJsonAnnotations() {
        if (additionalProperties == null) {
            return false;
        }
        final Boolean value = (Boolean) additionalProperties.get(ADD_PROP_GENERATE_JSON);
        return TRUE.equals(value);
    }

    private boolean generateXMLAnnotations() {
        if (additionalProperties == null) {
            return false;
        }
        final Boolean value = (Boolean) additionalProperties.get(ADD_PROP_GENERATE_XML);
        return TRUE.equals(value);
    }


    private enum NamingStrategy {
        AUTO,
        PATH;

        public static NamingStrategy from(final Object value) {
            if (value == null) {
                return AUTO;
            }

            return stream(values())
                .filter(strategy -> strategy.name().equalsIgnoreCase(value.toString()))
                .findAny()
                .orElse(AUTO);
        }
    }

}
