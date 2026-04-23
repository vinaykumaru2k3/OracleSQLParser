package com.migration.scanner.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class Summary {
    public int totalQueries;
    public int parsedQueries;
    public Map<String, Long> byType = new TreeMap<>();
    public Map<String, Long> byDifficulty = new TreeMap<>();
    public Map<String, Long> topTables = new LinkedHashMap<>();
    public Map<String, Long> topRisks = new LinkedHashMap<>();

    public static Summary from(List<Result> results) {
        Summary summary = new Summary();
        summary.totalQueries = results.size();
        summary.parsedQueries = (int) results.stream().filter(result -> result.sqlParsed).count();

        summary.byType = results.stream()
            .collect(Collectors.groupingBy(result -> result.statementType, TreeMap::new, Collectors.counting()));

        summary.byDifficulty = results.stream()
            .collect(Collectors.groupingBy(result -> result.difficulty, TreeMap::new, Collectors.counting()));

        Map<String, Long> tableCounts = results.stream()
            .flatMap(result -> result.tables.stream())
            .collect(Collectors.groupingBy(table -> table, Collectors.counting()));

        summary.topTables = tableCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> a,
                LinkedHashMap::new
            ));

        Map<String, Long> riskCounts = results.stream()
            .flatMap(result -> result.risks.stream())
            .collect(Collectors.groupingBy(risk -> risk, Collectors.counting()));

        summary.topRisks = riskCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> a,
                LinkedHashMap::new
            ));
        return summary;
    }
}
