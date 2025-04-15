package se.kth.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.StdDateFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The JsonUtils class provides a simple interface for writing and reading JSON files
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class JsonUtils {

    /** The default JSON file ending ".json" */
    public static final String JSON_FILE_ENDING = ".json";

    /** The string representing an empty JSON object */
    public static final String EMPTY_JSON_OBJECT = "{}";

    private static final ObjectMapper mapper =
        new ObjectMapper().setDateFormat(new StdDateFormat());

    private JsonUtils() { /* Nothing to see here... */ }

    /**
     * Read a JSON object from file
     * @param file the path to the JSON file to read.
     * @param jsonType the type that the data should be considered as.
     * @return an object of the specified type as read from the given file.
     */
    public static <T> T readFromFile(Path file, JavaType jsonType) {
        try {
            return mapper.readValue(Files.readString(file), jsonType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return a {@link TypeFactory} from the underlying {@link ObjectMapper}.
     */
    public static TypeFactory getTypeFactory() {
        return mapper.getTypeFactory();
    }
}
