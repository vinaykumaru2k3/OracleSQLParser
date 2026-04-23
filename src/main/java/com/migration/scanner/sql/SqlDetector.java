package com.migration.scanner.sql;

import java.util.Set;

public final class SqlDetector {
    private static final Set<String> KEYWORDS = Set.of(
        "SELECT", "INSERT", "UPDATE", "DELETE", "MERGE", "WITH", "CALL", "BEGIN"
    );

    private SqlDetector() {
    }

    public static boolean looksLikeSql(String value) {
        if (value == null) {
            return false;
        }
        String normalized = SqlNormalizer.normalize(value);
        for (String keyword : KEYWORDS) {
            if (normalized.startsWith(keyword) || normalized.contains(" " + keyword + " ")) {
                return true;
            }
        }
        return false;
    }
}
