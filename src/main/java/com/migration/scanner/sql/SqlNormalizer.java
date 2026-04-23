package com.migration.scanner.sql;

import java.util.Locale;

public final class SqlNormalizer {
    private SqlNormalizer() {
    }

    public static String normalize(String sql) {
        if (sql == null) {
            return "";
        }
        String withoutComments = sql
            .replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("(?m)--.*?$", " ");

        return withoutComments
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')
            .replaceAll("\\s+", " ")
            .trim()
            .toUpperCase(Locale.ROOT);
    }

    public static String cleanDisplay(String sql) {
        if (sql == null) {
            return "";
        }
        return sql
            .replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("(?m)--.*?$", " ")
            .replace('\r', ' ')
            .replace('\t', ' ')
            .replaceAll("[ ]{2,}", " ")
            .trim();
    }
}
