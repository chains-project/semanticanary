package se.kth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.dockerjava.api.model.HostConfig;
import org.apache.commons.lang3.tuple.Pair;
import picocli.CommandLine;
import se.kth.comparison.ValueComparator;
import se.kth.instrumentation.HostConfigBuilder;
import se.kth.instrumentation.ProjectExtractor;
import se.kth.matching.Difference;
import se.kth.matching.Matcher;
import se.kth.model.MethodInvocation;
import se.kth.util.Config;
import se.kth.util.DockerBuild;
import se.kth.util.FileUtils;
import se.kth.util.ResultsWriter;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

public class Semanticanary implements Callable<Integer> {

    @CommandLine.Option(
            names = {"-pre", "--preVersionImage"},
            description = "Name of the docker image of the pre-update version",
            required = true)
    String preVersionImage;

    @CommandLine.Option(
            names = {"-post", "--postVersionImage"},
            description = "Name of the docker image of the post-update version",
            required = true)
    String postVersionImage;

    @CommandLine.Option(
            names = {"-a", "--agentPath"},
            description = "Path to the jar of the semantic agent",
            required = true)
    Path semanticAgentPath;

    @CommandLine.Option(
            names = {"-m", "--targetMethod"},
            description = "Fully qualified name (\"fqn.your.TargetClass:targetMethod\") of the method to instrument",
            required = true)
    String targetMethod;

    @CommandLine.Option(
            names = {"-o", "--outputPath"},
            description = "Path to the directory where the output should be stored")
    Path outputPath;

    @Override
    public Integer call() throws Exception {
        boolean differencesFound = this.run("1", this.preVersionImage, this.postVersionImage, this.targetMethod);
        return differencesFound ? 1 : 0;
    }

    public boolean run(String id, String preImageName, String postImageName, String targetMethod) {
        DockerBuild dockerBuild = new DockerBuild(false);
        Path extractedProjectsOutputDir = Config.getTmpDirPath().resolve("instrumentation-output").resolve(id);
        FileUtils.ensureDirectoryExists(extractedProjectsOutputDir.getParent());
        HostConfigBuilder configBuilder = new HostConfigBuilder(semanticAgentPath.toString());
        HostConfig hostConfig = configBuilder.build();
        ProjectExtractor projectExtractor = new ProjectExtractor(dockerBuild, extractedProjectsOutputDir, hostConfig,
                targetMethod);

        Path preOutputPath = projectExtractor.extract(preImageName);
        Path postOutputPath = projectExtractor.extract(postImageName);

        List<Pair<MethodInvocation, MethodInvocation>> pairs = new Matcher().readAndMatch(preOutputPath,
                postOutputPath);

        try {
            List<List<Difference>> differences = ValueComparator.compareAllReturnValues(pairs);
            if (differences.stream()
                    .anyMatch(differences1 -> !differences1.isEmpty())) {
                System.out.println("Differences found:");
                differences.forEach(differences1 -> differences1.forEach(System.out::println));
                ResultsWriter.saveDifferences(differences, outputPath.resolve(id + ".json"));
                return true;
            } else {
                System.out.println("No Differences found");
                return false;
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Semanticanary()).execute(args);
        System.exit(exitCode);
    }
}