package org.drools.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.drools.drl.ast.descr.AndDescr;
import org.drools.drl.ast.descr.AnnotationDescr;
import org.drools.drl.ast.descr.AttributeDescr;
import org.drools.drl.ast.descr.BaseDescr;
import org.drools.drl.ast.descr.ExistsDescr;
import org.drools.drl.ast.descr.ExprConstraintDescr;
import org.drools.drl.ast.descr.FromDescr;
import org.drools.drl.ast.descr.FunctionDescr;
import org.drools.drl.ast.descr.FunctionImportDescr;
import org.drools.drl.ast.descr.GlobalDescr;
import org.drools.drl.ast.descr.ImportDescr;
import org.drools.drl.ast.descr.MVELExprDescr;
import org.drools.drl.ast.descr.NotDescr;
import org.drools.drl.ast.descr.OrDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drl.ast.descr.PatternDescr;
import org.drools.drl.ast.descr.RuleDescr;
import org.drools.drl.ast.descr.UnitDescr;

import static org.drools.parser.DRLParserHelper.getTextWithoutErrorNode;
import static org.drools.parser.ParserStringUtils.safeStripStringDelimiters;
import static org.drools.util.StringUtils.unescapeJava;

public class DRLVisitorImpl extends DRLParserBaseVisitor<Object> {

    private final PackageDescr packageDescr = new PackageDescr();

    private RuleDescr currentRule;
    private PatternDescr currentPattern;

    @Override
    public Object visitPackagedef(DRLParser.PackagedefContext ctx) {
        packageDescr.setName(getTextWithoutErrorNode(ctx.name));
        return super.visitPackagedef(ctx);
    }

    @Override
    public Object visitUnitdef(DRLParser.UnitdefContext ctx) {
        packageDescr.setUnit(new UnitDescr(ctx.name.getText()));
        return super.visitUnitdef(ctx);
    }

    @Override
    public Object visitGlobaldef(DRLParser.GlobaldefContext ctx) {
        GlobalDescr globalDescr = new GlobalDescr(ctx.drlIdentifier().getText(), ctx.type().getText());
        populateStartEnd(globalDescr, ctx);
        packageDescr.addGlobal(globalDescr);
        return super.visitGlobaldef(ctx);
    }

    @Override
    public Object visitImportdef(DRLParser.ImportdefContext ctx) {
        String target = ctx.drlQualifiedName().getText() + (ctx.MUL() != null ? ".*" : "");
        if (ctx.DRL_FUNCTION() != null || ctx.STATIC() != null) {
            FunctionImportDescr functionImportDescr = new FunctionImportDescr();
            functionImportDescr.setTarget(target);
            populateStartEnd(functionImportDescr, ctx);
            packageDescr.addFunctionImport(functionImportDescr);
        } else {
            ImportDescr importDescr = new ImportDescr();
            importDescr.setTarget(target);
            populateStartEnd(importDescr, ctx);
            packageDescr.addImport(importDescr);
        }
        return super.visitImportdef(ctx);
    }

    @Override
    public Object visitFunctiondef(DRLParser.FunctiondefContext ctx) {
        FunctionDescr functionDescr = new FunctionDescr();
        functionDescr.setNamespace(packageDescr.getNamespace());
        AttributeDescr dialect = packageDescr.getAttribute("dialect");
        if (dialect != null) {
            functionDescr.setDialect(dialect.getValue());
        }
        if (ctx.typeTypeOrVoid() != null) {
            functionDescr.setReturnType(ctx.typeTypeOrVoid().getText());
        } else {
            functionDescr.setReturnType("void");
        }
        functionDescr.setName(ctx.IDENTIFIER().getText());
        DRLParser.FormalParametersContext formalParametersContext = ctx.formalParameters();
        DRLParser.FormalParameterListContext formalParameterListContext = formalParametersContext.formalParameterList();
        if (formalParameterListContext != null) {
            List<DRLParser.FormalParameterContext> formalParameterContexts = formalParameterListContext.formalParameter();
            formalParameterContexts.forEach(formalParameterContext -> {
                DRLParser.TypeTypeContext typeTypeContext = formalParameterContext.typeType();
                DRLParser.VariableDeclaratorIdContext variableDeclaratorIdContext = formalParameterContext.variableDeclaratorId();
                functionDescr.addParameter(typeTypeContext.getText(), variableDeclaratorIdContext.getText());
            });
        }
        functionDescr.setBody(ParserStringUtils.getTextPreservingWhitespace(ctx.block()));
        packageDescr.addFunction(functionDescr);
        return super.visitFunctiondef(ctx);
    }

    @Override
    public Object visitRuledef(DRLParser.RuledefContext ctx) {
        currentRule = new RuleDescr(safeStripStringDelimiters(ctx.name.getText()));
        packageDescr.addRule(currentRule);

        Object result = super.visitRuledef(ctx);
        currentRule = null;
        return result;
    }

    @Override
    public Object visitLhs(DRLParser.LhsContext ctx) {
        if (ctx.lhsExpression() != null) {
            List<BaseDescr> descrList = visitLhsExpression(ctx.lhsExpression());
            descrList.forEach(descr -> currentRule.getLhs().addDescr(descr));
            slimRootDescr(currentRule.getLhs());
        }
        return null; // lhs is done for a rule
    }

    private void slimRootDescr(AndDescr root) {
        List<BaseDescr> descrList = new ArrayList<>(root.getDescrs());
        root.getDescrs().clear();
        descrList.forEach(root::addOrMerge); // This slims down nested AndDescr
    }

    @Override
    public List<BaseDescr> visitLhsExpression(DRLParser.LhsExpressionContext ctx) {
        return visitChildrenWithDescrAggregator(ctx, new ArrayList<>());
    }

    @Override
    public BaseDescr visitLhsPatternBind(DRLParser.LhsPatternBindContext ctx) {
        if (ctx.lhsPattern().size() == 1) {
            return getSinglePatternDescr(ctx);
        } else if (ctx.lhsPattern().size() > 1) {
            return getOrDescrWithMultiplePatternDescr(ctx);
        } else {
            throw new IllegalStateException("ctx.lhsPattern().size() == 0 : " + ctx.getText());
        }
    }

    private PatternDescr getSinglePatternDescr(DRLParser.LhsPatternBindContext ctx) {
        Optional<BaseDescr> optPatternDescr = visitFirstChild(ctx);
        PatternDescr patternDescr = optPatternDescr.filter(PatternDescr.class::isInstance)
                .map(PatternDescr.class::cast)
                .orElseThrow(() -> new IllegalStateException("lhsPatternBind must have at least one lhsPattern : " + ctx.getText()));
        if (ctx.label() != null) {
            patternDescr.setIdentifier(ctx.label().IDENTIFIER().getText());
        }
        return patternDescr;
    }

    private OrDescr getOrDescrWithMultiplePatternDescr(DRLParser.LhsPatternBindContext ctx) {
        OrDescr orDescr = new OrDescr();
        List<BaseDescr> patternList = visitChildrenWithDescrAggregator(ctx, new ArrayList<>());
        patternList.stream()
                .filter(PatternDescr.class::isInstance)
                .map(PatternDescr.class::cast)
                .forEach(patternDescr -> {
                    if (ctx.label() != null) {
                        patternDescr.setIdentifier(ctx.label().IDENTIFIER().getText());
                    }
                    orDescr.addDescr(patternDescr);
                });

        return orDescr;
    }

    @Override
    public PatternDescr visitLhsPattern(DRLParser.LhsPatternContext ctx) {
        PatternDescr patternDescr = new PatternDescr(ctx.objectType.getText());
        currentPattern = patternDescr;
        if (ctx.patternSource() != null) {
            String expression = ctx.patternSource().getText();
            FromDescr from = new FromDescr();
            from.setDataSource(new MVELExprDescr(expression));
            from.setResource(currentPattern.getResource());
            currentPattern.setSource(from);
        }
        super.visitLhsPattern(ctx);
        currentPattern = null;
        return patternDescr;
    }

    @Override
    public ExprConstraintDescr visitConstraint(DRLParser.ConstraintContext ctx) {
        Object constraint = super.visitConstraint(ctx);
        if (constraint != null) {
            String constraintString = constraint.toString();
            DRLParser.LabelContext label = ctx.label();
            if (label != null) {
                constraintString = label.getText() + constraintString;
            }
            ExprConstraintDescr constraintDescr = new ExprConstraintDescr(constraintString);
            constraintDescr.setType(ExprConstraintDescr.Type.NAMED);
            currentPattern.addConstraint(constraintDescr);
            return constraintDescr;
        }
        return null;
    }

    @Override
    public String visitDrlExpression(DRLParser.DrlExpressionContext ctx) {
        return ctx.children.stream()
                .map(c -> c instanceof TerminalNode ? c : c.accept(this))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.joining(" "));
    }

    @Override
    public String visitDrlPrimary(DRLParser.DrlPrimaryContext ctx) {
        return ctx.children.stream()
                .map(c -> c instanceof TerminalNode ? c : c.accept(this))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.joining(" "));
    }

    @Override
    public String visitDrlIdentifier(DRLParser.DrlIdentifierContext ctx) {
        return ctx.getText();
    }

    @Override
    public String visitDrlLiteral(DRLParser.DrlLiteralContext ctx) {
        ParseTree node = ctx;
        while (true) {
            if (node instanceof TerminalNode) {
                return node.toString();
            }
            if (node.getChildCount() != 1) {
                return super.visitDrlLiteral(ctx).toString();
            }
            node = node.getChild(0);
        }
    }

    @Override
    public Object visitDrlAnnotation(DRLParser.DrlAnnotationContext ctx) {
        AnnotationDescr annotationDescr = new AnnotationDescr(ctx.name.getText());
        annotationDescr.setValue(ctx.drlArguments().drlArgument(0).getText());
        currentRule.addAnnotation(annotationDescr);
        return super.visitDrlAnnotation(ctx);
    }

    @Override
    public Object visitAttribute(DRLParser.AttributeContext ctx) {
        AttributeDescr attributeDescr = new AttributeDescr(ctx.getChild(0).getText());
        if (ctx.getChildCount() > 1) {
            // TODO : will likely split visitAttribute methods using labels (e.g. #stringAttribute)
            String value = unescapeJava(safeStripStringDelimiters(ctx.getChild(1).getText()));
            attributeDescr.setValue(value);
        }
        if (currentRule != null) {
            currentRule.addAttribute(attributeDescr);
        } else {
            packageDescr.addAttribute(attributeDescr);
        }
        return super.visitAttribute(ctx);
    }

    @Override
    public ExistsDescr visitLhsExists(DRLParser.LhsExistsContext ctx) {
        ExistsDescr existsDescr = new ExistsDescr();
        BaseDescr descr = visitLhsPatternBind(ctx.lhsPatternBind());
        existsDescr.addDescr(descr);
        return existsDescr;
    }

    @Override
    public NotDescr visitLhsNot(DRLParser.LhsNotContext ctx) {
        NotDescr notDescr = new NotDescr();
        BaseDescr descr = visitLhsPatternBind(ctx.lhsPatternBind());
        notDescr.addDescr(descr);
        return notDescr;
    }

    @Override
    public BaseDescr visitLhsOr(DRLParser.LhsOrContext ctx) {
        if (!ctx.DRL_OR().isEmpty()) {
            OrDescr orDescr = new OrDescr();
            List<BaseDescr> descrList = visitChildrenWithDescrAggregator(ctx, new ArrayList<>());
            descrList.forEach(orDescr::addDescr);
            return orDescr;
        } else {
            // No DRL_OR means only one lhsAnd
            return visitLhsAnd(ctx.lhsAnd().get(0));
        }
    }

    @Override
    public BaseDescr visitLhsAnd(DRLParser.LhsAndContext ctx) {
        if (!ctx.DRL_AND().isEmpty()) {
            AndDescr andDescr = new AndDescr();
            List<BaseDescr> descrList = visitChildrenWithDescrAggregator(ctx, new ArrayList<>());
            descrList.forEach(andDescr::addDescr);
            return andDescr;
        } else {
            // No DRL_AND means only one lhsUnary
            return visitLhsUnary(ctx.lhsUnary().get(0));
        }
    }

    @Override
    public BaseDescr visitLhsUnary(DRLParser.LhsUnaryContext ctx) {
        return (BaseDescr) visitChildren(ctx);
    }

    @Override
    public Object visitRhs(DRLParser.RhsContext ctx) {
        currentRule.setConsequenceLocation(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()); // location of "then"
        currentRule.setConsequence(ParserStringUtils.getTextPreservingWhitespace(ctx.consequence()));
        return super.visitChildren(ctx);
    }

    public PackageDescr getPackageDescr() {
        return packageDescr;
    }

    private void populateStartEnd(BaseDescr descr, ParserRuleContext ctx) {
        descr.setStartCharacter(ctx.getStart().getStartIndex());
        // TODO: Current DRL6Parser adds +1 for EndCharacter but it doesn't look reasonable. At the moment, I don't add. Instead, I fix unit tests.
        //       I will revisit if this is the right approach.
        descr.setEndCharacter(ctx.getStop().getStopIndex());
    }

    private List<BaseDescr> visitChildrenWithDescrAggregator(RuleNode node, List<BaseDescr> aggregator) {
        int n = node.getChildCount();

        for (int i = 0; i < n && this.shouldVisitNextChild(node, aggregator); ++i) {
            ParseTree c = node.getChild(i);
            Object childResult = c.accept(this);
            if (childResult instanceof BaseDescr) {
                aggregator.add((BaseDescr) childResult);
            }
        }
        return aggregator;
    }

    private Optional<BaseDescr> visitFirstChild(RuleNode node) {
        int n = node.getChildCount();

        for (int i = 0; i < n && this.shouldVisitNextChild(node, null); ++i) {
            ParseTree c = node.getChild(i);
            Object childResult = c.accept(this);
            if (childResult instanceof BaseDescr) {
                return Optional.of((BaseDescr) childResult);
            }
        }
        return Optional.empty();
    }
}
