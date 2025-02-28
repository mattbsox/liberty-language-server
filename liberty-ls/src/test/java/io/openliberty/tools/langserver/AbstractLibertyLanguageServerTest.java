/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v. 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* SPDX-License-Identifier: EPL-2.0
*******************************************************************************/
package io.openliberty.tools.langserver;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.junit.AfterClass;

public class AbstractLibertyLanguageServerTest {
    protected static final String DUMMY_URI = "dummyUri";
    private String extensionUsed;
    protected static LibertyLanguageServer libertyLanguageServer;
    protected PublishDiagnosticsParams lastPublishedDiagnostics;

    public AbstractLibertyLanguageServerTest() {
        super();
    }

    @AfterClass
    public static void tearDown() {
        if (libertyLanguageServer != null) {
            libertyLanguageServer.shutdown();
        }
    }

    protected LibertyLanguageServer initializeLanguageServer(InputStream stream, String fileSuffix) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
            return initializeLanguageServer(br.lines().collect(Collectors.joining("\n")), fileSuffix);
        } catch (ExecutionException | InterruptedException | URISyntaxException | IOException e) {
            return null;
        }
    }

    protected LibertyLanguageServer initializeLanguageServerWithFilename(InputStream stream, String fileName) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
            return initializeLanguageServerWithFilename(br.lines().collect(Collectors.joining("\n")), fileName);
        } catch (ExecutionException | InterruptedException | URISyntaxException | IOException e) {
            return null;
        }
    }

    private LibertyLanguageServer initializeLanguageServer(String text, String fileSuffix) throws URISyntaxException, InterruptedException, ExecutionException {
        return initializeLanguageServer(fileSuffix, createTestTextDocument(text, fileSuffix));
    }

    private LibertyLanguageServer initializeLanguageServerWithFilename(String text, String filename) throws URISyntaxException, InterruptedException, ExecutionException {
        return initializeLanguageServer(filename.substring(filename.lastIndexOf(".")), createTestTextDocumentWithFilename(text, filename));
    }

    protected LibertyLanguageServer initializeLanguageServer(String fileSuffix, TextDocumentItem... items) throws URISyntaxException, InterruptedException, ExecutionException {
        this.extensionUsed = fileSuffix;
        initializeLanguageServer(getInitParams());
        for (TextDocumentItem item : items) {
            libertyLanguageServer.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(item));
        }
        return libertyLanguageServer;
    }


    private void initializeLanguageServer(InitializeParams params) throws InterruptedException, ExecutionException {
        libertyLanguageServer = new LibertyLanguageServer();
        libertyLanguageServer.connect(new DummyLanguageClient());
        libertyLanguageServer.startServer();
        CompletableFuture<InitializeResult> initialize = libertyLanguageServer.initialize(params);

        assertTrue(initialize.isDone());
        assertTrue(initialize.get().getCapabilities().getCompletionProvider().getResolveProvider());

        InitializedParams initialized = new InitializedParams();
        libertyLanguageServer.initialized(initialized);
    }
    
    private InitializeParams getInitParams() throws URISyntaxException {
        InitializeParams params = new InitializeParams();
        params.setProcessId(new Random().nextInt());
        // params.setWorkspaceFolders(getTestResource("/workspace"));
        return params;
    }

    private TextDocumentItem createTestTextDocument(String text, String fileSuffix) {
        return createTestTextDocumentWithFilename(text, DUMMY_URI + fileSuffix);
    }

    private TextDocumentItem createTestTextDocumentWithFilename(String text, String fileName) {
        return new TextDocumentItem(fileName, LibertyLanguageServer.LANGUAGE_ID, 0, text);
    }

    final class DummyLanguageClient implements LanguageClient {

        @Override
        public void telemetryEvent(Object object) {
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
            AbstractLibertyLanguageServerTest.this.lastPublishedDiagnostics = diagnostics;
        }

        @Override
        public void showMessage(MessageParams messageParams) {
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return null;
        }

        @Override
        public void logMessage(MessageParams message) {
        }
    }

    protected CompletableFuture<Either<List<CompletionItem>, CompletionList>> getCompletionFor(LibertyLanguageServer libertyLanguageServer, Position position) {
        return getCompletionFor(libertyLanguageServer, position, DUMMY_URI + extensionUsed);
    }

    protected CompletableFuture<Either<List<CompletionItem>, CompletionList>> getCompletionFor(LibertyLanguageServer libertyLanguageServer, Position position,
            String filename) {
        TextDocumentService tds = libertyLanguageServer.getTextDocumentService();
        CompletionParams completionParams = new CompletionParams(new TextDocumentIdentifier(filename), position);
        return tds.completion(completionParams);
    }

    protected CompletionItem createExpectedCompletionItem() {
        CompletionItem expectedItem = new CompletionItem("");
        return expectedItem;
    }
}
