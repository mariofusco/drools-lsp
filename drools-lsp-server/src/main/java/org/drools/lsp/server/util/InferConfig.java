package org.drools.lsp.server.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Logger;

public class InferConfig {
    private static final Logger LOG = Logger.getLogger("main");

    /**
     * Root of the workspace that is currently open in VSCode
     */
    private final Path workspaceRoot;

    public InferConfig(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    private static Path defaultMavenHome() {
        return Paths.get(System.getProperty("user.home")).resolve(".m2");
    }

    /**
     * Find .jar files for external dependencies, for examples maven dependencies in ~/.m2 or jars in bazel-genfiles
     */
    public String classPath() {
        // Maven
        var pomXml = workspaceRoot.resolve("pom.xml");
        if (Files.exists(pomXml)) {
            return mvnClassPath(pomXml);
        }
        return "";
    }

    static String mvnClassPath(Path pomXml) {
        Objects.requireNonNull(pomXml, "pom.xml path is null");
        try {
            // Run maven as a subprocess
            var output = Files.createTempFile("java-language-server-maven-output", ".txt");
            String[] command = {
                    getMvnCommand(),
                    "--batch-mode", // Turns off ANSI control sequences
                    "validate",
                    "dependency:build-classpath",
                    "-DoutputAbsoluteArtifactFilename=true",
                    "-Dmdep.outputFile=" + output.toString()
            };
            LOG.info("Running " + String.join(" ", command) + " ...");
            var workingDirectory = pomXml.toAbsolutePath().getParent().toFile();
            var process =
                    new ProcessBuilder()
                            .command(command)
                            .directory(workingDirectory)
                            .redirectError(ProcessBuilder.Redirect.INHERIT)
                            .start();
            // Wait for process to exit
            var result = process.waitFor();
            if (result != 0) {
                LOG.severe("`" + String.join(" ", command) + "` returned " + result);
                return "";
            }
            // Read output
            return Files.readString(output);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String getMvnCommand() {
        var mvnCommand = "mvn";
        if (File.separatorChar == '\\') {
            mvnCommand = findExecutableOnPath("mvn.cmd");
            if (mvnCommand == null) {
                mvnCommand = findExecutableOnPath("mvn.bat");
            }
        }
        return mvnCommand;
    }

    private static String findExecutableOnPath(String name) {
        for (var dirname : System.getenv("PATH").split(File.pathSeparator)) {
            var file = new File(dirname, name);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}
