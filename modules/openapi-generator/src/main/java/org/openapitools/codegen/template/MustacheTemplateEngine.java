package org.openapitools.codegen.template;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;

import org.openapitools.codegen.CodegenConfig;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

/**
 * A template engine using {@code mustache.js} syntax.
 *
 * @author Simon Marti
 */
public class MustacheTemplateEngine extends AbstractTemplateEngine {

    static final String MUSTACHE_EXTENSION = "mustache";

    private final Mustache.Compiler compiler;

    /**
     * Creates a new instance with the given {@code config}.
     *
     * @param config the {@link CodegenConfig}.
     */
    public MustacheTemplateEngine(CodegenConfig config) {
        super(config);
        compiler = Mustache.compiler()
                .withLoader(new MustacheTemplateEngine.TemplateLoader())
                .defaultValue("");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String renderTemplate(String name, Map<String, Object> data) throws IOException {
        final Template compiledTemplate = compiler.compile(findTemplateFile(name).getReader());
        return compiledTemplate.execute(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFileExtension() {
        return MUSTACHE_EXTENSION;
    }

    /**
     * Template loader which uses the configured {@link TemplateLocator} to load template files.
     */
    private class TemplateLoader implements Mustache.TemplateLoader {
        @Override
        public Reader getTemplate(String name) throws Exception {
            return findTemplateFile(name).getReader();
        }
    }

}
