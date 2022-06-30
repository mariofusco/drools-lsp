package org.drools.lsp.server.util;

import org.kie.memorycompiler.KieMemoryCompiler;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ProjectClassloader extends URLClassLoader {
    protected Path projectRoot;
    protected Map<String, String> javaSourcesMap = new HashMap<>();


    public ProjectClassloader(Path projectRoot, URL[] urls) {
        super(urls);
        this.projectRoot = projectRoot;
        initialize();
    }

    public void initialize() {
        try {
            Files.walk(projectRoot).filter(p -> p.toFile().getName().endsWith(".java")).forEach(p -> {
                try {
                    javaSourcesMap.put(pathToClassName(p), Files.readString(p));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String pathToClassName(Path path) {
        return path.toString()
                .replace(projectRoot.toString(), "")
                .replace("/src/main/java/", "")
                .replace(".java", "")
                .replace("/", ".");
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String[] parts = name.split("\\$");
        String clsPath = parts[0].replace('.', '/');

        Path sourcePath = projectRoot.resolve("src/main/java/" + clsPath.concat(".java"));
        Path classPath = projectRoot.resolve("target/classes/" + clsPath.concat(".class"));

        // Check if the compiled .class is up-to-date
        if (!sourcePath.toFile().exists() || sourcePath.toFile().lastModified() <= classPath.toFile().lastModified()) {
            // Use the default Classloader
            return super.findClass(name);
        }

        // Compile in-memory
        try {
            String source = Files.readString(sourcePath);
            javaSourcesMap.put(name, source);
            Map<String, Class<?>> compiled = KieMemoryCompiler.compile(javaSourcesMap, this);  // TODO: use ProjectClassLoader from drools?
            return compiled.get(name);  // TODO: check when $
        } catch (Exception e) {
            throw new ClassNotFoundException("Failed compiling source file at " + sourcePath, e);
        }
    }
}
