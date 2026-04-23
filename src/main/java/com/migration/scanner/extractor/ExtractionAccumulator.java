package com.migration.scanner.extractor;

import com.github.javaparser.ast.expr.Expression;
import com.migration.scanner.model.Result;
import com.migration.scanner.sql.SqlDetector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ExtractionAccumulator {
    private final String packageName;
    private final String className;
    private final Path file;
    private final Set<String> localDedupe = new LinkedHashSet<>();
    final List<Result> results = new ArrayList<>();

    ExtractionAccumulator(String packageName, String className, Path file) {
        this.packageName = packageName;
        this.className = className;
        this.file = file;
    }

    void maybeAdd(Expression expression, int line, JavaScope scope) {
        ExpressionResolver.resolve(expression, scope)
            .ifPresent(sql -> maybeAdd(sql, line, "JAVA"));
    }

    void maybeAdd(String sql, int line, String sourceType) {
        if (!SqlDetector.looksLikeSql(sql)) {
            return;
        }
        Result result = Result.build(packageName, className, file, line, sourceType, sql);
        if (localDedupe.add(result.normalizedQuery)) {
            results.add(result);
        }
    }
}
