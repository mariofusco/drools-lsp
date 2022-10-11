package org.drools.lsp.server.util;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.drools.compiler.compiler.*;
import org.drools.compiler.lang.descr.ImportDescr;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.kie.internal.jci.CompilationProblem;

import java.util.LinkedList;
import java.util.List;

public class ErrorDiagnostics {

    public static List<Diagnostic> errorToDiagnostics(DroolsError error) {
        List<Diagnostic> diagnostics = new LinkedList<>();
        int line;
        if (error.getLines().length == 0 || error.getLines()[0] < 0)
            line = 0;
        else
            line = error.getLines()[0];

        if (error instanceof RuleBuildError) {
            CompilationProblem[] obj = (CompilationProblem[]) ((RuleBuildError) error).getObject();
            for (var p : obj) {
                diagnostics.add(new Diagnostic(
                        new Range(new Position(line + p.getStartLine() - 2, p.getStartColumn()), new Position(line + p.getEndLine() - 2, p.getEndColumn())),
                        p.getMessage()
                ));
            }

        } else if (error instanceof ParserError) {
            int row = ((ParserError) error).getRow();
            if (row > 0) row--;
            diagnostics.add(new Diagnostic(
                    new Range(new Position(row, ((ParserError) error).getCol()),
                            new Position(row, ((ParserError) error).getCol() + 1)),
                    error.getMessage()
            ));

        } else if (error instanceof DescrBuildError) {
            diagnostics.add(new Diagnostic(
                    new Range(new Position(((DescrBuildError) error).getDescr().getLine() - 1, ((DescrBuildError) error).getDescr().getColumn()),
                            new Position(((DescrBuildError) error).getDescr().getEndLine() - 1, ((DescrBuildError) error).getDescr().getEndColumn())),
                    error.getMessage()
            ));

        } else if (error instanceof ImportError) {
            try {
                ImportDescr descr = (ImportDescr) FieldUtils.getField(ImportError.class, "importDescr", true).get(error);

                diagnostics.add(new Diagnostic(
                        new Range(new Position(descr.getLine() - 1, descr.getColumn()), new Position(descr.getEndLine() - 1, descr.getEndColumn())),
                        error.getMessage()
                ));
            } catch (Exception e) {
                throw new RuntimeException("Failed to get importDescr");
            }

        } else if (error instanceof FunctionError) {
            diagnostics.add(new Diagnostic(
                    new Range(new Position(((FunctionError) error).getFunctionDescr().getLine() - 1, ((FunctionError) error).getFunctionDescr().getColumn()),
                            new Position(((FunctionError) error).getFunctionDescr().getEndLine() - 1, ((FunctionError) error).getFunctionDescr().getEndColumn())),
                    error.getMessage()
            ));

        } else if (error instanceof GlobalError) {
            diagnostics.add(new Diagnostic(
                    new Range(new Position(line, 0), new Position(line, 999)),
                    error.getMessage()
            ));

        } else {
            diagnostics.add(new Diagnostic(
                    new Range(new Position(line, 0), new Position(line, 1)),
                    error.getMessage()
            ));
        }
        return diagnostics;
    }

    public static DiagnosticSeverity getDiagnosticSeverity(DroolsError error) {
        switch (error.getSeverity()) {
            case ERROR:
                return DiagnosticSeverity.Error;
            case WARNING:
                return DiagnosticSeverity.Warning;
            case INFO:
                return DiagnosticSeverity.Information;
            default:
                throw new RuntimeException("Unknown severity " + error.getSeverity());
        }
    }
}
