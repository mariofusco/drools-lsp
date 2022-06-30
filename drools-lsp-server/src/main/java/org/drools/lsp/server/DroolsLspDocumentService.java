package org.drools.lsp.server;

import org.drools.compiler.builder.impl.KnowledgeBuilderImpl;
import org.drools.compiler.compiler.DroolsError;
import org.drools.completion.DRLCompletionHelper;
import org.drools.core.io.impl.ReaderResource;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.lsp.server.util.ErrorDiagnostics;
import org.drools.lsp.server.util.ProjectClassloader;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.kie.api.KieServices;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.internal.builder.KnowledgeBuilderFactory;

import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.drools.lsp.server.DroolsLspServer.projectClassPathUrls;
import static org.drools.parser.DRLParserHelper.parse;


public class DroolsLspDocumentService implements TextDocumentService {

    private final Map<String, String> sourcesMap = new ConcurrentHashMap<>();
    private final Map<String, Resource> resourcesMap = new ConcurrentHashMap<>();

    private final DroolsLspServer server;
    private KnowledgeBuilderImpl knowledgeBuilder;
    private ClassLoader classLoader;


    public DroolsLspDocumentService(DroolsLspServer server) {
        this.server = server;
    }

    public void init() {
        KieServices kieServices = KieServices.Factory.get();
        classLoader = new URLClassLoader(projectClassPathUrls.toArray(new URL[]{}));
//        classLoader = new ProjectClassloader(DroolsLspServer.projectRoot, projectClassPathUrls.toArray(new URL[]{}));

//        knowledgeBuilder = (KnowledgeBuilderImpl) KnowledgeBuilderFactory.newKnowledgeBuilder();
        knowledgeBuilder = (KnowledgeBuilderImpl) KnowledgeBuilderFactory.newKnowledgeBuilder(KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration(null, classLoader));
//        knowledgeBuilder = new KnowledgeBuilderImpl((InternalKnowledgeBase) kieServices.getKieClasspathContainer(cl).newKieBase(null));
    }


    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        sourcesMap.put(params.getTextDocument().getUri(), params.getTextDocument().getText());
        CompletableFuture.runAsync(() ->
                server.getClient().publishDiagnostics(
                        new PublishDiagnosticsParams(params.getTextDocument().getUri(), validate(params.getTextDocument().getUri()))
                )
        );
    }

    private List<Diagnostic> validate(String documentId) {
        List<DroolsError> errors = getErrors(documentId);

        List<Diagnostic> diagnostics = new LinkedList<>();
        for (DroolsError e : errors) {
            diagnostics.addAll(ErrorDiagnostics.errorToDiagnostics(e));
        }

        return diagnostics;
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        sourcesMap.put(params.getTextDocument().getUri(), params.getContentChanges().get(0).getText());
        // modify internal state
//        this.documentVersions.put(params.getTextDocument().getUri(), params.getTextDocument().getVersion() + 1);
        // send notification
        CompletableFuture.runAsync(() ->
                server.getClient().publishDiagnostics(
                        new PublishDiagnosticsParams(params.getTextDocument().getUri(), validate(params.getTextDocument().getUri()))
                )
        );
    }

    public String getRuleName(CompletionParams completionParams) {
        String text = sourcesMap.get(completionParams.getTextDocument().getUri());
        PackageDescr packageDescr = parse(text);
        return packageDescr.getRules().get(0).getName();
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams completionParams) {
        return CompletableFuture.supplyAsync(() -> Either.forLeft(attempt(() -> getCompletionItems(completionParams))));
    }

    private <T> T attempt(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            server.getClient().showMessage(new MessageParams(MessageType.Error, e.toString()));
        }
        return null;
    }

    public List<CompletionItem> getCompletionItems(CompletionParams completionParams) {
        String text = sourcesMap.get(completionParams.getTextDocument().getUri());

        Position caretPosition = completionParams.getPosition();
        List<CompletionItem> completionItems = DRLCompletionHelper.getCompletionItems(text, caretPosition, server.getClient());

        server.getClient().showMessage(new MessageParams(MessageType.Info, "Position=" + caretPosition));
        server.getClient().showMessage(new MessageParams(MessageType.Info, "completionItems = " + completionItems));

        return completionItems;
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
    }


    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        // TODO
        return TextDocumentService.super.formatting(params);
    }


    private List<DroolsError> getErrors(String documentId) {
        try {
//            knowledgeBuilder.resetProblems();
//            for(var p : knowledgeBuilder.getPackageNames()) {
//                for(var pd : knowledgeBuilder.getPackageDescrs(p)) {
//                    pd.
//                }
//            }

//            if (resourcesMap.containsKey(documentId))
//                knowledgeBuilder.removeObjectsGeneratedFromResource(resourcesMap.remove(documentId));
            knowledgeBuilder = (KnowledgeBuilderImpl) KnowledgeBuilderFactory.newKnowledgeBuilder(KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration(null, classLoader));

            Reader reader = new StringReader(sourcesMap.get(documentId));
            Resource resource = new ReaderResource(reader, ResourceType.DRL);
            resourcesMap.put(documentId, resource);

//            KieContainer container = KieServices.Factory.get().getKieClasspathContainer(classLoader);
//
////            BuildContext buildContext = new BuildContext();
//            for (String kb : container.getKieBaseNames()) {
//                KieBaseModelImpl kbModel = (KieBaseModelImpl) container.getKieBaseModel(kb);
//                KieProject project = (KieProject) FieldUtils.getField(KieContainerImpl.class, "kProject", true).get(container);
////                KnowledgeBuilderImpl kBuilder = (KnowledgeBuilderImpl) project.buildKnowledgePackages(kbModel, buildContext);
//                InternalKieModule kModule = project.getKieModuleForKBase(kbModel.getName());
//
//                KnowledgeBuilderImpl kBuilder = (KnowledgeBuilderImpl) KnowledgeBuilderFactory.newKnowledgeBuilder(kModule.createBuilderConfiguration(kbModel, classLoader));
//                CompositeKnowledgeBuilder ckbuilder = kBuilder.batch();
//
//                for(String fileName : kModule.getFileNames()) {
//                    if (!fileName.startsWith(".") && !fileName.endsWith(".properties") && KieBuilderImpl.filterFileInKBase(kModule, kbModel, fileName, () -> kModule.getResource(fileName), false)) {
//                        Path path = Paths.get(kModule.getResource(fileName)).toAbsolutePath();
//                        Reader reader;
//                        if (resourcesMap.containsKey(path)) {
//                            reader = new StringReader(resourcesMap.get(path));
//                        } else {
//                            reader = new FileReader(fileName);
//                        }
//                        ckbuilder.add(new ReaderResource(reader, ResourceType.DRL));
//                    }
//                }
//                ckbuilder.build();
//
//                // TODO: filter results for this file
//
//                return List.of(kBuilder.getErrors().getErrors());
//            }

            knowledgeBuilder.add(resource, ResourceType.DRL);
            return List.of(knowledgeBuilder.getErrors().getErrors());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Collections.emptyList();
    }
}
