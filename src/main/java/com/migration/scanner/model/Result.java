package com.migration.scanner.model;

import com.migration.scanner.sql.SqlAnalyzer;
import com.migration.scanner.sql.SqlNormalizer;
import java.nio.file.Path;

public final class Result {
    public String pkg;
    public String cls;
    public String path;
    public String sourceType;
    public int line;
    public String query;
    public String normalizedQuery;
    public String statementType;
    public java.util.List<String> tables;
    public java.util.List<String> columns;
    public java.util.List<String> joins;
    public java.util.List<String> risks;
    public int riskScore;
    public String difficulty;
    public boolean sqlParsed;
    public String parseError;

    public static Result build(String pkg, String cls, Path file, int line, String sourceType, String rawSql) {
        Result result = new Result();
        result.pkg = pkg;
        result.cls = cls;
        result.path = file.toAbsolutePath().toString();
        result.line = line;
        result.sourceType = sourceType;
        result.query = SqlNormalizer.cleanDisplay(rawSql);
        result.normalizedQuery = SqlNormalizer.normalize(rawSql);

        SqlAnalysis analysis = SqlAnalyzer.analyze(rawSql);
        result.statementType = analysis.statementType;
        result.tables = analysis.tables;
        result.columns = analysis.columns;
        result.joins = analysis.joins;
        result.risks = analysis.risks;
        result.riskScore = analysis.riskScore;
        result.difficulty = analysis.difficulty;
        result.sqlParsed = analysis.sqlParsed;
        result.parseError = analysis.parseError;
        return result;
    }
}
