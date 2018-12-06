package org.openapitools.codegen.template;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.openapitools.codegen.CodegenConfig;

/**
 * A template engine which selects other template engines to use, based on the template's file extension.
 *
 * <p>Currently the following file extensions and template engines are supported:
 * <ul>
 *     <li>{@code .mustache}: {@link MustacheTemplateEngine}
 *     <li>{@code .hbs}: {@link HandlebarsTemplateEngine}
 * </ul>
 *
 * <p>Files with extensions other than the ones above must be referenced by full name and will be copied as-is using the {@link ExactCopyTemplateEngine}.
 *
 * <p>Additional template engines can be registered by code generators using {@link #registerEngine(TemplateEngine)}.
 *
 * @author Simon Marti
 */
public class MultiTemplateEngine extends AbstractTemplateEngine {
    private final Map<String, TemplateEngine> templateEngines = new HashMap<>();

    private final ExactCopyTemplateEngine exactCopyTemplateEngine;

    public static TemplateLocator multiTemplateLocator() {
        return new MultiTemplateEngine(null).templateLocator;
    }

    public MultiTemplateEngine(CodegenConfig config) {
        super(config);

        registerEngine(new MustacheTemplateEngine(config));
        registerEngine(new HandlebarsTemplateEngine(config));

        exactCopyTemplateEngine = new ExactCopyTemplateEngine(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String renderTemplate(String name, Map<String, Object> data) throws IOException {
        final TemplatePath template = findTemplateFile(name);
        final String extension = FilenameUtils.getExtension(template.getPath());

        if (templateEngines.containsKey(extension)) {
            return templateEngines.get(extension).renderTemplate(name, data);
        }

        return exactCopyTemplateEngine.renderTemplate(name, data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFileExtension() {
        return null;
    }

    /**
     * Register an additional template engine for files with the engines predefined file extension (as determined by {@link TemplateEngine#getFileExtension()}).
     *
     * @param engine the template engine to add
     */
    public void registerEngine(TemplateEngine engine) {
        templateEngines.put(engine.getFileExtension(), engine);
        templateLocator.addTemplateExtension(engine.getFileExtension());
    }
}
