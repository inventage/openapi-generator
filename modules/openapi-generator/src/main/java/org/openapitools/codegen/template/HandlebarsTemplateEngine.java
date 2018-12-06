package org.openapitools.codegen.template;

import static com.google.common.base.Objects.equal;

import java.io.IOException;
import java.util.Map;

import org.openapitools.codegen.CodegenConfig;

import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.cache.ConcurrentMapTemplateCache;
import com.github.jknack.handlebars.context.FieldValueResolver;
import com.github.jknack.handlebars.context.JavaBeanValueResolver;
import com.github.jknack.handlebars.context.MapValueResolver;
import com.github.jknack.handlebars.context.MethodValueResolver;
import com.github.jknack.handlebars.io.*;


/**
 * A template engine using {@code handlebars} syntax.
 *
 * @author Tobias Gmünder
 * @author Simon Marti
 */
public class HandlebarsTemplateEngine extends AbstractTemplateEngine {

    public static final String HANDLEBARS_EXTENSION = "hbs";

    private final Handlebars handlebars;

    /**
     * Creates a new instance with the given {@code config}.
     *
     * @param config the {@link CodegenConfig}.
     */
    public HandlebarsTemplateEngine(CodegenConfig config) {
        super(config);
        handlebars = new Handlebars()
                .with(new CodegenTemplateLoader())
                .with(new ConcurrentMapTemplateCache())
                .prettyPrint(true);

        registerHelpers();
    }

    /**
     * Registers helper methods on the {@link Handlebars} compiler, usable in templates.
     */
    protected void registerHelpers() {
        HumanizeHelper.register(handlebars);

        handlebars.registerHelper("equals", new Helper<Object>() {
            @Override
            public Object apply(Object context, Options options) throws IOException {
                final Object obj = options.param(0);
                return equal(context, obj) ? options.fn() : options.inverse();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String renderTemplate(String templateName, Map<String, Object> templateData) throws IOException {
        final Template template = handlebars.compile(templateName);

        final Context context = Context
                .newBuilder(templateData)
                .resolver(
                        MapValueResolver.INSTANCE,
                        FieldValueResolver.INSTANCE,
                        MethodValueResolver.INSTANCE,
                        JavaBeanValueResolver.INSTANCE
                ).build();

        return template.apply(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFileExtension() {
        return HANDLEBARS_EXTENSION;
    }

    private class CodegenTemplateLoader extends AbstractTemplateLoader {
        CodegenTemplateLoader() {
            setPrefix("");
            setSuffix("");
        }

        @Override
        public TemplateSource sourceAt(String name) throws IOException {
            return new CodegenTemplateSource(name);
        }
    }

    private class CodegenTemplateSource extends AbstractTemplateSource {
        private final String name;

        public CodegenTemplateSource(String name) {
            this.name = name;
        }

        @Override
        public String content() throws IOException {
            return readTemplate(name);
        }

        @Override
        public String filename() {
            return name;
        }

        @Override
        public long lastModified() {
            // We don't want to use the automatic reloading system, so we keep this value constant.
            return 0;
        }
    }
}
