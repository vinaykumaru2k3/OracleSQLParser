package com.migration.scanner.extractor;

import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

final class ExpressionResolver {
    private static final int MAX_DEPTH = 24;

    private ExpressionResolver() {
    }

    static Optional<String> resolve(Expression expression, JavaScope scope) {
        return resolve(expression, scope, 0);
    }

    private static Optional<String> resolve(Expression expression, JavaScope scope, int depth) {
        if (expression == null || depth > MAX_DEPTH) {
            return Optional.empty();
        }

        if (expression instanceof StringLiteralExpr) {
            return Optional.of(((StringLiteralExpr) expression).asString());
        }
        if (expression instanceof TextBlockLiteralExpr) {
            return Optional.of(((TextBlockLiteralExpr) expression).stripIndent());
        }
        if (expression instanceof LiteralStringValueExpr) {
            return Optional.of(((LiteralStringValueExpr) expression).getValue());
        }
        if (expression instanceof EnclosedExpr) {
            return resolve(((EnclosedExpr) expression).getInner(), scope, depth + 1);
        }
        if (expression instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expression;
            if (binary.getOperator() == BinaryExpr.Operator.PLUS) {
                Optional<String> left = resolve(binary.getLeft(), scope, depth + 1);
                Optional<String> right = resolve(binary.getRight(), scope, depth + 1);
                if (left.isPresent() && right.isPresent()) {
                    return Optional.of(left.get() + right.get());
                }
            }
        }
        if (expression instanceof NameExpr) {
            return scope.getString(((NameExpr) expression).getNameAsString());
        }
        if (expression instanceof FieldAccessExpr) {
            return scope.getString(((FieldAccessExpr) expression).getNameAsString());
        }
        if (expression instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expression;
            if (isStringFormat(call)) {
                return resolveStringFormat(call, scope, depth + 1);
            }
            if ("concat".equals(call.getNameAsString()) && call.getScope().isPresent() && call.getArguments().size() == 1) {
                Optional<String> base = resolve(call.getScope().get(), scope, depth + 1);
                Optional<String> arg = resolve(call.getArgument(0), scope, depth + 1);
                if (base.isPresent() && arg.isPresent()) {
                    return Optional.of(base.get() + arg.get());
                }
            }
            if ("toString".equals(call.getNameAsString())) {
                return resolveBuilder(call, scope);
            }
            if (isBuilderAppend(call)) {
                return resolveBuilder(call, scope);
            }
        }
        if (expression instanceof ObjectCreationExpr) {
            return resolveBuilder(expression, scope);
        }
        if (expression instanceof ArrayInitializerExpr) {
            ArrayInitializerExpr array = (ArrayInitializerExpr) expression;
            String joined = array.getValues().stream()
                .map(value -> resolve(value, scope, depth + 1).orElse(""))
                .collect(Collectors.joining(","));
            return joined.isBlank() ? Optional.empty() : Optional.of(joined);
        }
        return Optional.empty();
    }

    static boolean isBuilderInitializer(Expression expression) {
        if (!(expression instanceof ObjectCreationExpr)) {
            return false;
        }
        String type = ((ObjectCreationExpr) expression).getType().asString();
        return "StringBuilder".equals(type) || "StringBuffer".equals(type);
    }

    static boolean isBuilderAppend(MethodCallExpr call) {
        return "append".equals(call.getNameAsString()) && call.getArguments().size() == 1;
    }

    static Optional<String> resolveBuilder(Expression expression, JavaScope scope) {
        return resolveBuilder(expression, scope, 0);
    }

    private static Optional<String> resolveBuilder(Expression expression, JavaScope scope, int depth) {
        if (expression == null || depth > MAX_DEPTH) {
            return Optional.empty();
        }
        if (expression instanceof ObjectCreationExpr) {
            ObjectCreationExpr creation = (ObjectCreationExpr) expression;
            String type = creation.getType().asString();
            if ("StringBuilder".equals(type) || "StringBuffer".equals(type)) {
                if (creation.getArguments().isEmpty()) {
                    return Optional.of("");
                }
                return resolve(creation.getArgument(0), scope, depth + 1);
            }
        }
        if (expression instanceof NameExpr) {
            return scope.getBuilder(((NameExpr) expression).getNameAsString());
        }
        if (expression instanceof FieldAccessExpr) {
            return scope.getBuilder(((FieldAccessExpr) expression).getNameAsString());
        }
        if (expression instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expression;
            if ("toString".equals(call.getNameAsString()) && call.getScope().isPresent()) {
                return resolveBuilder(call.getScope().get(), scope, depth + 1);
            }
            if (isBuilderAppend(call) && call.getScope().isPresent()) {
                Optional<String> base = resolveBuilder(call.getScope().get(), scope, depth + 1);
                Optional<String> addition = resolve(call.getArgument(0), scope, depth + 1);
                if (base.isPresent() && addition.isPresent()) {
                    return Optional.of(base.get() + addition.get());
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isStringFormat(MethodCallExpr call) {
        if (!"format".equals(call.getNameAsString()) || call.getArguments().isEmpty()) {
            return false;
        }
        return call.getScope()
            .map(scope -> "String".equals(scope.toString()))
            .orElse(false);
    }

    private static Optional<String> resolveStringFormat(MethodCallExpr call, JavaScope scope, int depth) {
        Optional<String> format = resolve(call.getArgument(0), scope, depth + 1);
        if (format.isEmpty()) {
            return Optional.empty();
        }

        List<String> values = new ArrayList<>();
        for (int i = 1; i < call.getArguments().size(); i++) {
            values.add(resolve(call.getArgument(i), scope, depth + 1).orElse("?"));
        }
        return Optional.of(applyFormat(format.get(), values));
    }

    private static String applyFormat(String template, List<String> values) {
        StringBuilder output = new StringBuilder();
        int argIndex = 0;
        for (int i = 0; i < template.length(); i++) {
            char ch = template.charAt(i);
            if (ch == '%' && i + 1 < template.length()) {
                char next = template.charAt(i + 1);
                if (next == '%') {
                    output.append('%');
                    i++;
                    continue;
                }
                if (argIndex < values.size()) {
                    output.append(values.get(argIndex++));
                } else {
                    output.append('%').append(next);
                }
                i++;
                continue;
            }
            output.append(ch);
        }
        return output.toString();
    }
}
