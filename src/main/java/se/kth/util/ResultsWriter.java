package se.kth.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.matching.Difference;
import se.kth.model.BenchmarkResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ResultsWriter {

    private static final Logger logger = LoggerFactory.getLogger(ResultsWriter.class);

    public static void saveDifferences(List<List<Difference>> differences, Path path) {
        ResultsWriter.saveObject(differences, path);
    }

    public static void saveBenchmarkResult(List<BenchmarkResult> result, Path path) {
        ResultsWriter.saveObject(result, path);
    }

    private static void saveObject(Object object, Path path) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try {
            FileUtils.ensureDirectoryExists(path.getParent());
            mapper.writeValue(new File(path.toString()), object);
            logger.info("Results written to: {}", path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
