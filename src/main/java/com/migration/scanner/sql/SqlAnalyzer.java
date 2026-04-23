package com.migration.scanner.sql;

import com.migration.scanner.model.SqlAnalysis;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.util.TablesNamesFinder;

public final class SqlAnalyzer {
    private static final LinkedHashMap<String, Integer> RISK_WEIGHTS = new LinkedHashMap<>();

    static {
        RISK_WEIGHTS.put("NVL", 2);
        RISK_WEIGHTS.put("DECODE", 3);
        RISK_WEIGHTS.put("ROWNUM", 4);
        RISK_WEIGHTS.put("ORACLE_JOIN(+)", 5);
        RISK_WEIGHTS.put("CONNECT_BY", 5);
        RISK_WEIGHTS.put("START_WITH", 3);
        RISK_WEIGHTS.put("MINUS", 3);
        RISK_WEIGHTS.put("SYSDATE", 1);
        RISK_WEIGHTS.put("SYSTIMESTAMP", 2);
        RISK_WEIGHTS.put("TO_DATE", 1);
        RISK_WEIGHTS.put("TO_CHAR", 1);
        RISK_WEIGHTS.put("LISTAGG", 2);
        RISK_WEIGHTS.put("MERGE", 2);
    }

    private SqlAnalyzer() {
    }

    public static SqlAnalysis analyze(String rawSql) {
        SqlAnalysis analysis = new SqlAnalysis();
        String cleaned = SqlNormalizer.cleanDisplay(rawSql);
        String normalized = SqlNormalizer.normalize(rawSql);
        analysis.statementType = detectType(normalized);

        LinkedHashSet<String> tables = new LinkedHashSet<>();
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        LinkedHashSet<String> joins = new LinkedHashSet<>();
        LinkedHashSet<String> risks = new LinkedHashSet<>();

        try {
            Statements statements = CCJSqlParserUtil.parseStatements(cleaned);
            analysis.sqlParsed = true;
            for (net.sf.jsqlparser.statement.Statement statement : statements.getStatements()) {
                collectTables(statement, tables);
                collectStatementShape(statement, columns, joins, risks);
            }
        } catch (JSQLParserException ex) {
            analysis.sqlParsed = false;
            analysis.parseError = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        }

        applyRawSqlRisks(normalized, risks);
        int score = risks.stream()
            .mapToInt(risk -> RISK_WEIGHTS.getOrDefault(risk, 1))
            .sum();

        analysis.tables = List.copyOf(tables);
        analysis.columns = List.copyOf(columns);
        analysis.joins = List.copyOf(joins);
        analysis.risks = List.copyOf(risks);
        analysis.riskScore = score;
        analysis.difficulty = score <= 2 ? "LOW" : score <= 6 ? "MEDIUM" : "HIGH";
        return analysis;
    }

    private static void collectTables(net.sf.jsqlparser.statement.Statement statement, Set<String> tables) {
        TablesNamesFinder finder = new TablesNamesFinder();
        try {
            List<String> names = finder.getTableList(statement);
            for (String name : names) {
                if (name != null && !name.isBlank() && !"DUAL".equalsIgnoreCase(name)) {
                    tables.add(name.toUpperCase(Locale.ROOT));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void collectStatementShape(net.sf.jsqlparser.statement.Statement statement,
                                              Set<String> columns,
                                              Set<String> joins,
                                              Set<String> risks) {
        SqlAstWalker walker = new SqlAstWalker(columns, joins, risks);
        statement.accept(walker);
    }

    private static void applyRawSqlRisks(String normalized, Set<String> risks) {
        if (normalized.contains("ROWNUM")) {
            risks.add("ROWNUM");
        }
        if (normalized.contains("(+)")) {
            risks.add("ORACLE_JOIN(+)");
        }
        if (normalized.contains("CONNECT BY")) {
            risks.add("CONNECT_BY");
        }
        if (normalized.contains("START WITH")) {
            risks.add("START_WITH");
        }
        if (normalized.contains(" MINUS ")) {
            risks.add("MINUS");
        }
        if (normalized.startsWith("MERGE ")) {
            risks.add("MERGE");
        }
    }

    private static String detectType(String normalized) {
        if (normalized.startsWith("SELECT") || normalized.startsWith("WITH")) {
            return "SELECT";
        }
        if (normalized.startsWith("INSERT")) {
            return "INSERT";
        }
        if (normalized.startsWith("UPDATE")) {
            return "UPDATE";
        }
        if (normalized.startsWith("DELETE")) {
            return "DELETE";
        }
        if (normalized.startsWith("MERGE")) {
            return "MERGE";
        }
        if (normalized.startsWith("CALL")) {
            return "CALL";
        }
        if (normalized.startsWith("BEGIN")) {
            return "PLSQL";
        }
        return "OTHER";
    }
}
