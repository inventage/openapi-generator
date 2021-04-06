package org.openapitools.codegen.languages;

import com.google.common.collect.ImmutableMap;
import io.swagger.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.templating.HandlebarsEngineAdapter;
import org.openapitools.codegen.utils.GeneratorUtils;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.lang.Character.isUpperCase;
import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static org.openapitools.codegen.utils.GeneratorUtils.constantName;
import static org.openapitools.codegen.utils.GeneratorUtils.groupOperationsByOperationId;
import static org.openapitools.codegen.utils.ModelUtils.isArraySchema;
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

    private static final Logger LOG = LoggerFactory.getLogger(InventageJavaServerCodegen.class);

    protected static final String JAX_RS = "jax-rs";
    protected static final String SPRING = "spring";

    // Inventage Vendor Extensions (IVE)

    /** Name of vendor extension to indicate an 'enum' with no compile time dependency to the whole value set is desired. */
    private static final String IVE_XENUMERATION = "x-enumeration";

    /** Name of the the vendor extension used to indicate the Java type of an 'x-enumeration'. Only used internally in the generator. */
    private static final String IVE_XENUMERATION_TYPE = "x-enumeration-type";



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
        embeddedTemplateDir = templateDir = "InventageJava";
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

    /** {@inheritDoc} */
    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "inventage-java";
    }

    /** {@inheritDoc} */
    @Override
    public String getHelp() {
        return "Generates a JAX-RS server stub.";
    }

    /** {@inheritDoc} */
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

        additionalProperties.put("jackson", "true");
        additionalProperties.put("useBeanValidation", "true");

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

        if (isXEnumeration(p)) {
            codegenProperty.openApiType = getXEnumerationType(p);
            codegenProperty.dataType = getXEnumerationType(p);
            codegenProperty.datatypeWithEnum = getXEnumerationType(p);
            codegenProperty.baseType = getXEnumerationType(p);

            if (ModelUtils.isStringSchema(p)) {
                codegenProperty.isString = false;
                codegenProperty.minimum = null;
                codegenProperty.minLength = null;
                codegenProperty.maximum = null;
                codegenProperty.maxLength = null;
            }
        }
        return codegenProperty;
    }

    @Override
    protected void setNonArrayMapProperty(CodegenProperty property, String type) {
        super.setNonArrayMapProperty(property, type);

        if (isXEnumeration(property)) {
            property.isPrimitiveType = false;
        }
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);

        shortAppName = GeneratorUtils.extractShortAppName(additionalProperties, openAPI);

        final String apiFolder = sourceFolder + File.separator + apiPackage.replace('.', '/');
        if (JAX_RS.equals(getLibrary())) {
            supportingFiles.add(new SupportingFile("application.hbs", apiFolder, format(Locale.ENGLISH,"Abstract%sApplication.java", shortAppName)));
        }

        additionalProperties.put("swaggerFileApplication", true);

        // add full swagger definition in a mustache parameter
        final String swaggerDef = Json.pretty(openAPI);
        this.additionalProperties.put("fullSwagger", swaggerDef);
    }

    @Override
    public CodegenModel fromModel(String name, Schema model) {
        final CodegenModel codegenModel = super.fromModel(name, model);

        if (isXEnumeration(model)) {
            model.getExtensions().put("x-enumeration-type", name);
        }

        codegenModel.imports.remove("ApiModelProperty");
        codegenModel.imports.remove("ApiModel");
        if (codegenModel.dataType != null && codegenModel.vendorExtensions.containsKey("x-enumeration")) {
            if (additionalProperties.containsKey("jackson")) {
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
            if (additionalProperties.containsKey("jackson")) {
                codegenModel.imports.add("JsonProperty");
                codegenModel.imports.add("JsonInclude");
                codegenModel.imports.add("JsonInclude.Include");
                if (codegenModel.vars.stream().anyMatch(p -> p.isEnum)) {
                    codegenModel.imports.add("JsonValue");
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
            //Needed imports for Jackson's JsonValue
            if (additionalProperties.containsKey("jackson")) {
                codegenModel.imports.add("JsonValue");
            }
        }

        if (hasInnerEnum(codegenModel)) {
            codegenModel.imports.add("JsonValue");
        }

        if (serializableModel) {
            codegenModel.imports.add("Serializable");
        }

        for (CodegenProperty cgp : codegenModel.vars) {
            if (cgp.vendorExtensions.containsKey("x-use-offset-date-time")) {
                updateCgP(cgp, codegenModel);
            }
        }

        codegenModel.imports.add("XmlRootElement");
        codegenModel.imports.add("XmlAccessorType");
        codegenModel.imports.add("XmlAccessType");
        codegenModel.imports.add("JacksonXmlProperty");
        codegenModel.imports.add("XmlElement");
        codegenModel.imports.add("XmlEnumValue");
        codegenModel.imports.add("XmlJavaTypeAdapter");
        codegenModel.imports.add("OffsetDateTimeXmlAdapter");
        codegenModel.imports.add("XmlAttribute");
        codegenModel.imports.add("JacksonXmlElementWrapper");
        codegenModel.imports.add("XmlElementWrapper");

        return codegenModel;
    }

    private void updateCgP(CodegenProperty cgp, CodegenModel codegenModel) {
        System.out.println("This model should use offsetDateTime: " + codegenModel.name + "." + cgp.name);
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
    public void addOperationToGroup(String tag, String resourcePath, io.swagger.v3.oas.models.Operation operation, CodegenOperation codegenOperation, Map<String, List<CodegenOperation>> operationGroups) {
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
            if (property.minimum.length() >= 10) {
                property.minimum += "L";
            }
        }
        if (property.maximum != null) {
            model.imports.add(isInt ? "Max" : "DecimalMax");
            if (property.maximum.length() >= 10) {
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
            System.out.println("Contains Key " + model.name + "." + property.name + ": " + property.datatypeWithEnum);
        }

        model.imports.remove("ApiModelProperty");
        model.imports.remove("ApiModel");
        model.imports.remove("JsonSerialize");
        model.imports.remove("ToStringSerializer");
        model.imports.remove("JsonValue");
        model.imports.remove("JsonProperty");
    }

    @Override
    protected String getOrGenerateOperationId(io.swagger.v3.oas.models.Operation operation, String path, String httpMethod) {
        if (operationNaming == NamingStrategy.PATH) {
            operation.setOperationId(null);
        }

        return super.getOrGenerateOperationId(operation, path, httpMethod);
    }

    /** {@inheritDoc} */
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
    public Map<String, Object> postProcessOperationsWithModels(Map<String, Object> objs, List<Object> allModels) {
        final Map<String, Object> newObjs = super.postProcessOperationsWithModels(objs, allModels);
        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");

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


    /** {@inheritDoc} */
    @Override
    public String getterAndSetterCapitalize(String name) {
        name = toVarName(name).codePoints()
                .filter(Character::isLetterOrDigit)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        if (name.length() > 1 && isUpperCase(name.charAt(1))) {
            return name;
        }
        else {
            return camelize(name);
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

    /** {@inheritDoc} */
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
