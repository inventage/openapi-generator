package org.openapitools.codegen.template;

import static java.util.Arrays.asList;
import static org.openapitools.codegen.template.MustacheTemplateEngine.MUSTACHE_EXTENSION;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CodegenConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Locates template files in configurable locations.
 *
 * @see #findTemplateFile(CodegenConfig, String)
 *
 * @author Simon Marti
 */
public class TemplateLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateLocator.class);
    private static final String LEGACY_TEMPLATE_EXTENSION = "." + MUSTACHE_EXTENSION;

    private final List<String> templateExtensions = new ArrayList<>();

    /**
     * Creates a new instance which locates template files by exact match only.
     */
    public TemplateLocator() {
        templateExtensions.add(null);
    }

    /**
     * Creates a new instance which locates template files with the supplied extensions.
     *
     * @param templateExtensions the file extensions to search for, with no leading period mark (e.g. {@code mustache})
     */
    public TemplateLocator(Collection<String> templateExtensions) {
        this();
        for (String extension : templateExtensions) {
            addTemplateExtension(extension);
        }
    }

    /**
     * Creates a new instance which locates template files with the supplied extensions.
     *
     * @param templateExtensions the file extensions to search for, with no leading period mark (e.g. {@code mustache})
     */
    public TemplateLocator(String... templateExtensions) {
        this(asList(templateExtensions));
    }

    /**
     * Adds an additional file extension to this {@code TemplateLocator}.
     *
     * <br>Any calls to {@link #findTemplateFile(CodegenConfig, String)} will now search for this extension after all previously configured ones.
     *
     * @param extension the file extension to add
     */
    public void addTemplateExtension(String extension) {
        if (!templateExtensions.contains(extension)) {
            templateExtensions.add(extension);
        }
    }

    /**
     * Find a template file by name.
     *
     * <p> 1st the code will check if there's a &lt;template folder&gt;/libraries/&lt;library&gt; folder containing the file
     * <br>2nd it will check for the file in the specified &lt;template folder&gt; folder
     * <br>3rd it will check if there's an &lt;embedded template&gt;/libraries/&lt;library&gt; folder containing the file
     * <br>4th and last it will assume the file is in &lt;embedded template&gt; folder.
     *
     * <p>The {@code name} parameter can be an exact filename or a basename without an extension. In the latter case, the basename is expanded with one
     * configured extension after the other, until a matching file is found.
     *
     * @param config the configuration
     * @param name the basename of the template
     * @return the path to the found template, or {@code null} if none was found
     */
    public TemplatePath findTemplateFile(CodegenConfig config, String name) {
        // Remove .mustache extension
        if (name.endsWith(LEGACY_TEMPLATE_EXTENSION)) {
            name = name.substring(0, name.length() - LEGACY_TEMPLATE_EXTENSION.length());
        }

        // Check the supplied template library folder for the file
        final String library = config.getLibrary();
        if (StringUtils.isNotEmpty(library)) {
            // Look for the file in the library subfolder of the supplied template
            final TemplatePath libraryTemplatePath = findTemplateInFolder(name, buildLibraryPath(config.templateDir(), library));
            if (libraryTemplatePath != null) {
                return libraryTemplatePath;
            }
        }

        // Check the supplied template main folder for the file
        final TemplatePath mainTemplatePath = findTemplateInFolder(name, Paths.get(config.templateDir()));
        if (mainTemplatePath != null) {
            return mainTemplatePath;
        }

        // Try the embedded template library folder next
        if (StringUtils.isNotEmpty(library)) {
            final TemplatePath libraryTemplatePath = findTemplateOnClasspath(name, buildLibraryPath(config.embeddedTemplateDir(), library));
            if (libraryTemplatePath != null) {
                return libraryTemplatePath;
            }
        }

        // Fall back to the template file embedded/packaged in the JAR file...
        final TemplatePath embeddedTemplatePath = findTemplateOnClasspath(name, Paths.get(config.embeddedTemplateDir()));
        if (embeddedTemplatePath != null) {
            return embeddedTemplatePath;
        }

        // And as a last resort to the common template directory "_common"
        return findTemplateOnClasspath(name, Paths.get(config.getCommonTemplateDir()));
    }

    /**
     * On systems with a {@link File#separator} other than "{@code /}", this normalizes a path to use "{@code /}" as the file separator.
     *
     * @param path the path to normalize
     * @return the normalized path
     */
    public String getCPResourcePath(String path) {
        if (!"/".equals(File.separator)) {
            return path.replaceAll(Pattern.quote(File.separator), "/");
        }
        return path;
    }

    private Path buildLibraryPath(String templateDir, String library) {
        return Paths.get(templateDir, "libraries", library);
    }

    private TemplatePath findTemplateInFolder(String name, Path folder) {
        for (String extension : templateExtensions) {
            final String filename = buildFilename(name, extension);
            final Path path = folder.resolve(filename);
            if (Files.exists(path)) {
                return new TemplatePath.FilePath(path);
            }
        }
        return null;
    }

    private TemplatePath findTemplateOnClasspath(String name, Path folder) {
        for (String extension : templateExtensions) {
            final String filename = buildFilename(name, extension);
            final String path = getCPResourcePath(folder.resolve(filename).toString());
            final URL resource = getClass().getClassLoader().getResource(path);
            if (resource != null) {
                return new TemplatePath.ResourcePath(resource);
            }
        }
        return null;
    }

    private String buildFilename(String name, String extension) {
        if (extension == null) {
            return name;
        } else {
            return name + "." + extension;
        }
    }
}
