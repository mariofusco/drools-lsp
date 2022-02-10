package org.drools.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import junit.framework.TestCase;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageClient;

import static org.junit.Assert.assertEquals;

public class DRLCompletionHelperTest extends TestCase {

    public void testGetCompletionItems() {

        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));
        String text = "suggestion";

        Position caretPosition = completionParams.getPosition();

        List<CompletionItem> result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        CompletionItem completionItem = result.get(0);
        assertEquals("suggestion", completionItem.getInsertText());
    }

    public void testTestGetCompletionItems() {
    }

    public void testCreateCompletionItem() {
    }

    public void testCreateDuplicateTextDummyItem() {
    }

    private LanguageClient getLanguageClient() {
        List<Diagnostic> diagnostics = new ArrayList<>();
       return  new LanguageClient() {
            @Override
            public void telemetryEvent(Object object) {
            }

            @Override
            public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
                return null;
            }

            @Override
            public void showMessage(MessageParams messageParams) {
            }

            @Override
            public void publishDiagnostics(PublishDiagnosticsParams d) {
                diagnostics.clear();
                diagnostics.addAll(d.getDiagnostics());
            }

            @Override
            public void logMessage(MessageParams message) {
            }
        };

    }
}