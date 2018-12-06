package org.openapitools.codegen.template;

import static java.nio.file.Files.newInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Path;

import org.apache.commons.io.Charsets;

/**
 * Path to a template file on the file system or classpath.
 *
 * @author Simon Marti
 */
public abstract class TemplatePath {

    /**
     * Opens the file at this path as a {@link InputStream}.
     *
     * @return an {@link InputStream} of the file at this path
     * @throws IOException if the file does not exist
     */
    public abstract InputStream getInputStream() throws IOException;

    /**
     * Opens the file at this path as a {@link Reader} with UTF-8 encoding.
     *
     * @return an {@link Reader} of the file at this path
     * @throws IOException if the file does not exist
     */
    public Reader getReader() throws IOException {
        return new InputStreamReader(getInputStream(), Charsets.UTF_8);
    }

    /**
     * Returns the relative path to the template file.
     *
     * @return the relative path to the template file
     */
    public abstract String getPath();

    /**
     * A template file on the file system.
     */
    public static class FilePath extends TemplatePath {
        private final Path path;

        public FilePath(Path path) {
            this.path = path;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (path != null) {
                return newInputStream(path);
            }
            return null;
        }

        @Override
        public String getPath() {
            return path.toString();
        }
    }

    /**
     * A template file on the classpath.
     */
    public static class ResourcePath extends TemplatePath {
        private final URL resource;

        public ResourcePath(URL resource) {
            this.resource = resource;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (resource != null) {
                return resource.openStream();
            }
            return null;
        }

        @Override
        public String getPath() {
            return resource.getPath();
        }
    }
}
