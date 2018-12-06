package org.openapitools.codegen.template;

import java.io.IOException;
import java.util.Map;

import org.openapitools.codegen.CodegenConfig;


/**
 * A template engine which does not alter the template files in any way.
 *
 * @author Simon Marti
 */
public class ExactCopyTemplateEngine extends AbstractTemplateEngine {

    /**
     * Creates a new instance with the given {@code config}.
     *
     * @param config  the {@link CodegenConfig}.
     */
    public ExactCopyTemplateEngine(CodegenConfig config) {
        super(config);
    }

    /**
     * Returns the content of the provided file.
     *
     * @param name the name of the template to read
     * @param data will be ignored
     * @return the content of {@code file}
     * @throws IOException if the file does not exist
     */
    @Override
    public String renderTemplate(String name, Map<String, Object> data) throws IOException {
        return readTemplate(findTemplateFile(name));
    }

    @Override
    public String getFileExtension() {
        return null;
    }

}
