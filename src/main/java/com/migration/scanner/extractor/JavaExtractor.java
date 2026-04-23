package com.migration.scanner.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.migration.scanner.model.Result;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class JavaExtractor {
    private static final Set<String> SQL_SINKS = Set.of(
        "preparestatement",
        "preparesql",
        "createquery",
        "createnativequery",
        "query",
        "execute",
        "executequery",
        "executenativequery",
        "addbatch"
    );

    private JavaExtractor() {
    }

    public static List<Result> extract(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        ParseResult<CompilationUnit> parseResult = configuredParser().parse(source);
        if (!parseResult.getResult().isPresent()) {
            return List.of();
        }

        CompilationUnit cu = parseResult.getResult().get();
        String packageName = cu.getPackageDeclaration()
            .map(pkg -> pkg.getName().asString())
            .orElse("default");
        String className = cu.getPrimaryTypeName().orElse(file.getFileName().toString());

        JavaScope classScope = new JavaScope();
        seedFieldConstants(cu, classScope);

        ExtractionAccumulator accumulator = new ExtractionAccumulator(packageName, className, file);
        extractAnnotationSql(cu, accumulator, classScope);

        for (TypeDeclaration<?> type : cu.getTypes()) {
            if (type instanceof ClassOrInterfaceDeclaration) {
                scanType((ClassOrInterfaceDeclaration) type, accumulator, classScope);
            }
        }
        return accumulator.results;
    }

    private static JavaParser configuredParser() {
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        return new JavaParser(configuration);
    }

    private static void seedFieldConstants(CompilationUnit cu, JavaScope classScope) {
        boolean changed;
        do {
            changed = false;
            for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
                for (var variable : field.getVariables()) {
                    if (!variable.getInitializer().isPresent()) {
                        continue;
                    }
                    Optional<String> resolved = ExpressionResolver.resolve(variable.getInitializer().get(), classScope);
                    if (resolved.isPresent() && classScope.putString(variable.getNameAsString(), resolved.get())) {
                        changed = true;
                    }
                    if (ExpressionResolver.isBuilderInitializer(variable.getInitializer().get())) {
                        String builderText = ExpressionResolver.resolveBuilder(variable.getInitializer().get(), classScope).orElse("");
                        if (classScope.putBuilder(variable.getNameAsString(), builderText)) {
                            changed = true;
                        }
                    }
                }
            }
        } while (changed);
    }

    private static void extractAnnotationSql(CompilationUnit cu, ExtractionAccumulator accumulator, JavaScope scope) {
        for (AnnotationExpr annotation : cu.findAll(AnnotationExpr.class)) {
            if (annotation instanceof MarkerAnnotationExpr) {
                continue;
            }
            if (annotation instanceof SingleMemberAnnotationExpr) {
                SingleMemberAnnotationExpr single = (SingleMemberAnnotationExpr) annotation;
                accumulator.maybeAdd(single.getMemberValue(), lineOf(annotation), scope);
                continue;
            }
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normal.getPairs()) {
                accumulator.maybeAdd(pair.getValue(), lineOf(pair), scope);
            }
        }
    }

    private static void scanType(ClassOrInterfaceDeclaration type, ExtractionAccumulator accumulator, JavaScope parentScope) {
        JavaScope typeScope = parentScope.copy();

        type.getMethods().forEach(method ->
            method.getBody().ifPresent(body -> scanStatements(body.getStatements(), accumulator, typeScope.copy()))
        );

        type.getMembers().stream()
            .filter(member -> member instanceof com.github.javaparser.ast.body.ConstructorDeclaration)
            .map(member -> (com.github.javaparser.ast.body.ConstructorDeclaration) member)
            .forEach(ctor -> scanStatements(ctor.getBody().getStatements(), accumulator, typeScope.copy()));

        type.getMembers().stream()
            .filter(member -> member instanceof com.github.javaparser.ast.body.InitializerDeclaration)
            .map(member -> (com.github.javaparser.ast.body.InitializerDeclaration) member)
            .forEach(init -> scanStatements(init.getBody().getStatements(), accumulator, typeScope.copy()));
    }

    private static void scanStatements(com.github.javaparser.ast.NodeList<Statement> statements,
                                       ExtractionAccumulator accumulator,
                                       JavaScope scope) {
        for (Statement statement : statements) {
            scanStatement(statement, accumulator, scope);
        }
    }

    private static void scanStatement(Statement statement, ExtractionAccumulator accumulator, JavaScope scope) {
        if (statement.isBlockStmt()) {
            scanStatements(statement.asBlockStmt().getStatements(), accumulator, scope.copy());
            return;
        }

        if (statement instanceof ExpressionStmt) {
            handleExpression(statement.asExpressionStmt().getExpression(), accumulator, scope, lineOf(statement));
            return;
        }

        if (statement instanceof ReturnStmt) {
            ReturnStmt returnStmt = (ReturnStmt) statement;
            returnStmt.getExpression().ifPresent(expr -> accumulator.maybeAdd(expr, lineOf(statement), scope));
            return;
        }

        if (statement instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) statement;
            scanStatement(ifStmt.getThenStmt(), accumulator, scope.copy());
            ifStmt.getElseStmt().ifPresent(elseStmt -> scanStatement(elseStmt, accumulator, scope.copy()));
            return;
        }

        if (statement instanceof ForStmt) {
            ForStmt forStmt = (ForStmt) statement;
            JavaScope loopScope = scope.copy();
            for (Expression expression : forStmt.getInitialization()) {
                handleExpression(expression, accumulator, loopScope, lineOf(expression));
            }
            scanStatement(forStmt.getBody(), accumulator, loopScope);
            return;
        }

        if (statement instanceof ForEachStmt) {
            scanStatement(((ForEachStmt) statement).getBody(), accumulator, scope.copy());
            return;
        }

        if (statement instanceof WhileStmt) {
            scanStatement(((WhileStmt) statement).getBody(), accumulator, scope.copy());
            return;
        }

        if (statement instanceof DoStmt) {
            scanStatement(((DoStmt) statement).getBody(), accumulator, scope.copy());
            return;
        }

        if (statement instanceof TryStmt) {
            TryStmt tryStmt = (TryStmt) statement;
            scanStatement(tryStmt.getTryBlock(), accumulator, scope.copy());
            tryStmt.getCatchClauses().forEach(catchClause -> scanStatement(catchClause.getBody(), accumulator, scope.copy()));
            tryStmt.getFinallyBlock().ifPresent(finallyBlock -> scanStatement(finallyBlock, accumulator, scope.copy()));
            return;
        }

        if (statement instanceof SwitchStmt) {
            SwitchStmt switchStmt = (SwitchStmt) statement;
            for (SwitchEntry entry : switchStmt.getEntries()) {
                scanStatements(entry.getStatements(), accumulator, scope.copy());
            }
        }
    }

    private static void handleExpression(Expression expression,
                                         ExtractionAccumulator accumulator,
                                         JavaScope scope,
                                         int defaultLine) {
        if (expression instanceof VariableDeclarationExpr) {
            VariableDeclarationExpr declaration = (VariableDeclarationExpr) expression;
            declaration.getVariables().forEach(variable -> {
                variable.getInitializer().ifPresent(initializer -> {
                    if (ExpressionResolver.isBuilderInitializer(initializer)) {
                        ExpressionResolver.resolveBuilder(initializer, scope)
                            .ifPresent(text -> scope.putBuilder(variable.getNameAsString(), text));
                    }
                    ExpressionResolver.resolve(initializer, scope)
                        .ifPresent(value -> scope.putString(variable.getNameAsString(), value));
                    accumulator.maybeAdd(initializer, lineOf(variable), scope);
                });
            });
            return;
        }

        if (expression instanceof AssignExpr) {
            AssignExpr assign = (AssignExpr) expression;
            String targetName = nameOf(assign.getTarget());
            if (targetName != null) {
                if (ExpressionResolver.isBuilderInitializer(assign.getValue())) {
                    ExpressionResolver.resolveBuilder(assign.getValue(), scope)
                        .ifPresent(text -> scope.putBuilder(targetName, text));
                } else if (assign.getValue().isMethodCallExpr() && ExpressionResolver.isBuilderAppend(assign.getValue().asMethodCallExpr())) {
                    ExpressionResolver.resolveBuilder(assign.getValue(), scope)
                        .ifPresent(text -> scope.putBuilder(targetName, text));
                }

                ExpressionResolver.resolve(assign.getValue(), scope)
                    .ifPresent(value -> scope.putString(targetName, value));
            }
            accumulator.maybeAdd(assign.getValue(), lineOf(assign), scope);
            return;
        }

        if (expression instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expression;
            if (ExpressionResolver.isBuilderAppend(call) && call.getScope().isPresent()) {
                String builderName = nameOf(call.getScope().get());
                if (builderName != null) {
                    ExpressionResolver.resolve(call.getArgument(0), scope)
                        .ifPresent(fragment -> scope.appendBuilder(builderName, fragment));
                }
            }

            String methodName = call.getNameAsString().toLowerCase(Locale.ROOT);
            if (SQL_SINKS.contains(methodName)) {
                for (Expression argument : call.getArguments()) {
                    accumulator.maybeAdd(argument, lineOf(call), scope);
                }
            } else {
                accumulator.maybeAdd(call, defaultLine, scope);
            }
            return;
        }

        accumulator.maybeAdd(expression, defaultLine, scope);
    }

    private static int lineOf(Node node) {
        return node.getBegin().map(position -> position.line).orElse(0);
    }

    private static String nameOf(Expression expression) {
        if (expression instanceof NameExpr) {
            return ((NameExpr) expression).getNameAsString();
        }
        if (expression instanceof FieldAccessExpr) {
            return ((FieldAccessExpr) expression).getNameAsString();
        }
        return null;
    }
}
