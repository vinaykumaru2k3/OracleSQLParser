package com.migration.scanner.report;

import com.migration.scanner.model.Summary;

public final class SummaryPrinter {
    private SummaryPrinter() {
    }

    public static void print(Summary summary, long elapsedMs) {
        System.out.println();
        System.out.println("===== Oracle Migration Summary =====");
        System.out.println("Total Queries: " + summary.totalQueries);
        System.out.println("Parsed by SQL AST: " + summary.parsedQueries);
        System.out.println("By Type: " + summary.byType);
        System.out.println("By Difficulty: " + summary.byDifficulty);
        System.out.println("Top Tables: " + summary.topTables);
        System.out.println("Top Risks: " + summary.topRisks);
        System.out.println("Elapsed: " + elapsedMs + " ms");
    }
}
