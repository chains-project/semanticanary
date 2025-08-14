package se.kth.util;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;

public class DockerBuild {

    static Logger log = LoggerFactory.getLogger(DockerBuild.class);
    private static DockerClient dockerClient;
    private static final int EXIT_CODE_OK = 0;

    public DockerBuild() {
        createDockerClient();
    }

    /**
     * Method to remove Docker container
     *
     * @param containerId - container id
     * @return boolean - true if container is removed successfully, false otherwise
     */
    public boolean removeContainer(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            log.warn("Failed to remove container with id: {}", containerId);
            return false;
        }
        log.info("Container with id: {} removed successfully", containerId);
        return true;
    }

    /**
     * Copies a project from a Docker container to a specified directory.
     *
     * @param containerId the ID of the Docker container
     * @param project     the name of the project to copy
     * @param dir         the directory to copy the project to
     * @return the path to the directory where the project was copied, or null if
     * the copy failed
     */
    public Path copyProjectFromContainer(String containerId, String project, Path dir) {
        try (InputStream dependencyStream = dockerClient.copyArchiveFromContainerCmd(containerId, "/" + project)
                .exec()) {
            copyFiles(dir, dependencyStream);
            log.info("Project {} copied successfully", project);
            return dir;
        } catch (Exception e) {
            log.error("Could not copy the project {}", project, e);
            return null;
        }
    }

    public Optional<String> createImageForRepositoryAtVersion(String baseImage, URL gitUrl, String versionTag,
                                                              String imageName, Path outputPath) {
        String projectDirectoryName = "project";

        log.info("Creating container for {} with version {} in {}", gitUrl, versionTag, baseImage);
        CreateContainerResponse container = dockerClient.createContainerCmd(baseImage)
                .withCmd("/bin/sh", "-c",
                        ("git clone --branch %s %s %s && cd %s && mvn test -B -l output.log -DtestFailureIgnore=true " +
                                "-Dmaven.test.failure.ignore=true").formatted(versionTag,
                                gitUrl, projectDirectoryName, projectDirectoryName))
                .exec();
        dockerClient.startContainerCmd(container.getId()).exec();
        WaitContainerResultCallback waitResult = dockerClient.waitContainerCmd(container.getId())
                .exec(new WaitContainerResultCallback());
        if (waitResult.awaitStatusCode() != EXIT_CODE_OK) {
            log.warn("Could not create docker image for project {} at version {} in {}", gitUrl, versionTag, baseImage);
            this.copyM2FolderToLocalPath(container.getId(), Paths.get("/project/output.log"), outputPath);
            dockerClient.removeContainerCmd(container.getId()).exec();
            return Optional.empty();
        } else {
            log.info("Successfully created docker image for project {} at version {} in {}", gitUrl, versionTag,
                    baseImage);
            this.copyM2FolderToLocalPath(container.getId(), Paths.get("/project/output.log"), outputPath);
            dockerClient.commitCmd(container.getId())
                    .withWorkingDir("/project")
                    .withRepository("ghcr.io/chains-project/breaking-updates")
                    .withTag(imageName)
                    .exec();
            dockerClient.removeContainerCmd(container.getId()).exec();
            return Optional.of(container.getId());
        }
    }

    private void createDockerClient() {
        DockerClientConfig clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                .withRegistryUrl("https://hub.docker.com")
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(clientConfig.getDockerHost())
                .sslConfig(clientConfig.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(30))
                .build();
        dockerClient = DockerClientImpl.getInstance(clientConfig, httpClient);
    }

    public void ensureBaseMavenImageExists(String image) throws InterruptedException {
        try {
            dockerClient.inspectImageCmd(image).exec();
        } catch (NotFoundException e) {
            log.info("Base image not present, pulling {}", image);
            log.info("Pulling Maven image {} ...", image);
            dockerClient.pullImageCmd(image)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();
            log.info("Done pulling Maven image {}", image);
        }
    }

    public void copyM2FolderToLocalPath(String containerId, Path fromContainer, Path localPath) {

        if (Files.notExists(localPath)) {
            try {
                log.info("Creating local path {}", localPath);
                Files.createDirectories(localPath);
            } catch (IOException e) {
                log.error("Could not create local path", e);
                throw new RuntimeException(e);
            }
        }
        log.info("");
        log.info("Copying folder {} from container to local path", localPath.getFileName());

        try (InputStream m2Stream = dockerClient.copyArchiveFromContainerCmd(containerId, fromContainer.toString())
                .exec()) {
            copyFiles(localPath, m2Stream);
            log.info("Folder {} copied successfully", localPath.getFileName());
        } catch (Exception e) {
            log.error("Could not copy the {} folder", localPath, e);
        }
    }

    private void copyFiles(Path localPath, InputStream m2Stream) throws IOException {
        try (TarArchiveInputStream tarStream = new TarArchiveInputStream(m2Stream)) {
            TarArchiveEntry entry;
            while ((entry = tarStream.getNextTarEntry()) != null) {
                if (!entry.isDirectory()) {
                    Path filePath = localPath.resolve(entry.getName());

                    if (!Files.exists(filePath)) {
                        Files.createDirectories(filePath.getParent());
                        Files.createFile(filePath);

                        byte[] fileContent = tarStream.readAllBytes();
                        Files.write(filePath, fileContent, StandardOpenOption.WRITE);
                    }
                }
            }
        }
    }

    /**
     * Starts a container which just spins infinitely long, meant to keep the
     * container alive and execute multiple
     * commands later on. The container must be killed manually!
     *
     * @param imageId    the docker image to use
     * @param hostConfig the HostConfig the container should be started with
     * @return the containerID of the started container
     */
    public String startSpinningContainer(String imageId, HostConfig hostConfig) {
        CreateContainerResponse container = dockerClient
                .createContainerCmd(imageId)
                .withHostConfig(hostConfig)
                .withEntrypoint("sh", "-c", "sleep 60")
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        return container.getId();
    }

    /**
     * Executes the given command inside an already running container and returns
     * the output.
     *
     * @param containerId the ID of the container to execute the command in
     * @param command     the command to execute
     * @return the output of the command
     */
    public String executeInContainer(String containerId, String... command) {

        ExecCreateCmdResponse response = dockerClient.execCreateCmd(containerId)
                .withCmd(command)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            dockerClient.execStartCmd(response.getId()).exec(new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame item) {
                    if (item.getStreamType() == StreamType.STDOUT || item.getStreamType() == StreamType.STDERR) {
                        try {
                            outputStream.write(item.getPayload());
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                }
            }).awaitCompletion();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}
