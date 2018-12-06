package org.openapitools.codegen.template;

import java.io.IOException;
import java.util.Map;

/**
 * Transforms a text document according to predefined rules and data provided at runtime.
 *
 * @author Simon Marti
 */
public interface TemplateEngine {
    /**
     * Processes the provided document, integrating the provided data where appropriate.
     *
     * @param name the template basename
     * @param data the data available to the template
     * @return the processed template text
     * @throws IOException if the template does not exist
     */
    String renderTemplate(String name, Map<String, Object> data) throws IOException;

    /**
     * Provides a default file extension for this {@link TemplateEngine}.
     *
     * @return the default file extension. May be {@code null} if this {@link TemplateEngine} works with any text file.
     */
    String getFileExtension();
}
