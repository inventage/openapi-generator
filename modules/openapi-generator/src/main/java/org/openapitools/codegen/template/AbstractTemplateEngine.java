package org.openapitools.codegen.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.openapitools.codegen.CodegenConfig;


/**
 * Transforms a text document according to predefined rules and data provided at runtime.
 *
 * @author Simon Marti
 */
public abstract class AbstractTemplateEngine implements TemplateEngine {

    protected final CodegenConfig config;
    protected final TemplateLocator templateLocator;

    /**
     * Creates a new instance with the given {@code config}.
     *
     * @param config  the {@link CodegenConfig}.
     */
    public AbstractTemplateEngine(CodegenConfig config) {
        this.config = config;
        this.templateLocator = new TemplateLocator(getFileExtension());
    }

    /**
     * Finds a template file using the local {@link TemplateLocator}.
     *
     * @param name the basename of the template
     * @return the path to the found template file
     * @throws IOException if no template with this name exists
     * @see TemplateLocator#findTemplateFile(CodegenConfig, String)
     */
    protected TemplatePath findTemplateFile(String name) throws IOException {
        final TemplatePath template = templateLocator.findTemplateFile(config, name);

        if (template == null) {
            throw new IOException("The template file '" + name + "' was not found.");
        }

        return template;
    }

    /**
     * Returns the complete content of the template file named {@code name}.
     *
     * @param name the basename of the template file
     * @return the complete content of the template file named {@code name}
     * @throws IOException if no such template file exists
     * @see TemplateLocator#findTemplateFile(CodegenConfig, String)
     */
    protected String readTemplate(String name) throws IOException {
        return readTemplate(findTemplateFile(name));
    }

    /**
     * Returns the complete content of the template file.
     *
     * @param template the template file
     * @return the complete content of the template file named {@code name}
     * @throws IOException if no such template file exists
     */
    protected String readTemplate(TemplatePath template) throws IOException {
        try (InputStream stream = template.getInputStream()) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }

}
