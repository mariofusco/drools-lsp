package org.drools.lsp.server;

import org.drools.lsp.server.util.InferConfig;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class DroolsLspServer implements LanguageServer, LanguageClientAware {

    private final DroolsLspDocumentService textService;
    private final WorkspaceService workspaceService;
    public static Path workspaceRoot;
    public static Path projectRoot;
    public static List<URL> projectClassPathUrls = new LinkedList<>();

    private LanguageClient client;

    public DroolsLspServer() {
        textService = new DroolsLspDocumentService(this);
        workspaceService = new DroolsLspWorkspaceService();
    }


    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    public LanguageClient getClient() {
        return client;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        workspaceRoot = Paths.get(params.getRootPath());
        try {
            List<Path> poms = Files.find(workspaceRoot, 100, (path, basicFileAttributes) -> path.endsWith("pom.xml")).collect(Collectors.toList());
            for (Path path : poms) {
                System.out.println("Found pom.xml at " + path);
                projectRoot = path.getParent();

//                projectClassPathUrls.add(path.getParent().resolve("src/main/java/").toUri().toURL());
                projectClassPathUrls.add(path.getParent().resolve("target/classes/").toUri().toURL());  // TODO: not the best way - should compile dynamically

                String projectClasspath = new InferConfig(path.getParent()).classPath();
                for (String p : projectClasspath.split(":"))
                    projectClassPathUrls.add(Paths.get(p).toUri().toURL());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        textService.init();

        // Initialize the InitializeResult for this LS.
        final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());

        // Set the capabilities of the LS to inform the client.
        initializeResult.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Full);
        CompletionOptions completionOptions = new CompletionOptions();
        initializeResult.getCapabilities().setCompletionProvider(completionOptions);
        return CompletableFuture.supplyAsync(() -> initializeResult);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public DroolsLspDocumentService getTextDocumentService() {
        return textService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }
}
