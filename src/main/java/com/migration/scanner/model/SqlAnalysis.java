package com.migration.scanner.model;

import java.util.List;

public final class SqlAnalysis {
    public String statementType = "OTHER";
    public List<String> tables = List.of();
    public List<String> columns = List.of();
    public List<String> joins = List.of();
    public List<String> risks = List.of();
    public int riskScore = 0;
    public String difficulty = "LOW";
    public boolean sqlParsed;
    public String parseError = "";
}
