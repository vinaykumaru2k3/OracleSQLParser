package com.migration.scanner.report;

import com.migration.scanner.model.Result;
import com.migration.scanner.model.Summary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class Exporter {
    private final Path outputDir;
    private final String timestamp;

    public Exporter(Path outputDir) throws IOException {
        this.outputDir = outputDir;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Files.createDirectories(outputDir);
    }

    public void writeCsv(List<Result> results) throws IOException {
        Path output = outputDir.resolve("oracle_scan_" + timestamp + ".csv");
        List<String> rows = new ArrayList<>();
        rows.add("Package,Class,Path,Line,SourceType,StatementType,SqlParsed,RiskScore,Difficulty,Risks,Tables,Columns,Joins,Query");
        for (Result result : results) {
            rows.add(String.join(",",
                csv(result.pkg),
                csv(result.cls),
                csv(result.path),
                Integer.toString(result.line),
                csv(result.sourceType),
                csv(result.statementType),
                Boolean.toString(result.sqlParsed),
                Integer.toString(result.riskScore),
                csv(result.difficulty),
                csv(String.join(" | ", result.risks)),
                csv(String.join(" | ", result.tables)),
                csv(String.join(" | ", result.columns)),
                csv(String.join(" | ", result.joins)),
                csv(result.query)
            ));
        }
        Files.write(output, rows, StandardCharsets.UTF_8);
        System.out.println("CSV: " + output.toAbsolutePath());
    }

    public void writeJson(List<Result> results, Summary summary) throws IOException {
        Path output = outputDir.resolve("oracle_scan_" + timestamp + ".json");
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"summary\": ").append(summaryJson(summary)).append(",\n");
        json.append("  \"results\": [\n");
        for (int i = 0; i < results.size(); i++) {
            json.append(resultJson(results.get(i)));
            if (i + 1 < results.size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        Files.writeString(output, json.toString(), StandardCharsets.UTF_8);
        System.out.println("JSON: " + output.toAbsolutePath());
    }

    public void writeHtml(List<Result> results, Summary summary) throws IOException {
        Path output = outputDir.resolve("oracle_scan_" + timestamp + ".html");
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        html.append("<title>Oracle Migration Report</title>");
        html.append("<style>");
        html.append("body{font-family:Arial,sans-serif;margin:24px;background:#f7f8fb;color:#18202a;}");
        html.append("h1,h2{margin:0 0 12px;} .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px;margin:16px 0 24px;}");
        html.append(".metric{background:#fff;padding:16px;border:1px solid #d9e0ea;border-radius:8px;} table{width:100%;border-collapse:collapse;background:#fff;border:1px solid #d9e0ea;border-radius:8px;overflow:hidden;}");
        html.append("th,td{padding:10px 12px;border-bottom:1px solid #e8edf3;text-align:left;vertical-align:top;font-size:13px;} th{background:#eef3f8;font-weight:600;}");
        html.append(".pill{display:inline-block;padding:2px 8px;border-radius:999px;background:#e9eff8;margin:0 6px 6px 0;font-size:12px;}");
        html.append(".HIGH{background:#fde7e7;} .MEDIUM{background:#fff0d8;} .LOW{background:#e6f4ea;}");
        html.append("</style></head><body>");
        html.append("<h1>Oracle Migration Report</h1>");
        html.append("<div class=\"grid\">");
        html.append(metric("Total Queries", Integer.toString(summary.totalQueries)));
        html.append(metric("Parsed by SQL AST", Integer.toString(summary.parsedQueries)));
        html.append(metric("High Risk", Long.toString(summary.byDifficulty.getOrDefault("HIGH", 0L))));
        html.append(metric("Medium Risk", Long.toString(summary.byDifficulty.getOrDefault("MEDIUM", 0L))));
        html.append("</div>");

        html.append("<h2>Top Tables</h2><p>");
        summary.topTables.forEach((table, count) -> html.append(pill(table + " (" + count + ")")));
        html.append("</p>");

        html.append("<h2>Top Risks</h2><p>");
        summary.topRisks.forEach((risk, count) -> html.append(pill(risk + " (" + count + ")")));
        html.append("</p>");

        html.append("<h2>Queries</h2><table><thead><tr>");
        html.append("<th>Location</th><th>Type</th><th>Difficulty</th><th>Risks</th><th>Tables</th><th>Columns</th><th>Query</th>");
        html.append("</tr></thead><tbody>");

        for (Result result : results) {
            html.append("<tr>");
            html.append("<td>").append(escapeHtml(result.path)).append(":").append(result.line).append("</td>");
            html.append("<td>").append(escapeHtml(result.statementType)).append("</td>");
            html.append("<td><span class=\"pill ").append(result.difficulty).append("\">").append(result.riskScore)
                .append(" / ").append(escapeHtml(result.difficulty)).append("</span></td>");
            html.append("<td>").append(joinPills(result.risks)).append("</td>");
            html.append("<td>").append(joinPills(result.tables)).append("</td>");
            html.append("<td>").append(joinPills(result.columns)).append("</td>");
            html.append("<td><pre>").append(escapeHtml(result.query)).append("</pre></td>");
            html.append("</tr>");
        }

        html.append("</tbody></table></body></html>");
        Files.writeString(output, html.toString(), StandardCharsets.UTF_8);
        System.out.println("HTML: " + output.toAbsolutePath());
    }

    private String summaryJson(Summary summary) {
        return "{\n" +
            "    \"totalQueries\": " + summary.totalQueries + ",\n" +
            "    \"parsedQueries\": " + summary.parsedQueries + ",\n" +
            "    \"byType\": " + mapJson(summary.byType) + ",\n" +
            "    \"byDifficulty\": " + mapJson(summary.byDifficulty) + ",\n" +
            "    \"topTables\": " + mapJson(summary.topTables) + ",\n" +
            "    \"topRisks\": " + mapJson(summary.topRisks) + "\n" +
            "  }";
    }

    private String resultJson(Result result) {
        return "    {\n" +
            "      \"package\": " + json(result.pkg) + ",\n" +
            "      \"class\": " + json(result.cls) + ",\n" +
            "      \"path\": " + json(result.path) + ",\n" +
            "      \"line\": " + result.line + ",\n" +
            "      \"sourceType\": " + json(result.sourceType) + ",\n" +
            "      \"statementType\": " + json(result.statementType) + ",\n" +
            "      \"sqlParsed\": " + result.sqlParsed + ",\n" +
            "      \"riskScore\": " + result.riskScore + ",\n" +
            "      \"difficulty\": " + json(result.difficulty) + ",\n" +
            "      \"risks\": " + listJson(result.risks) + ",\n" +
            "      \"tables\": " + listJson(result.tables) + ",\n" +
            "      \"columns\": " + listJson(result.columns) + ",\n" +
            "      \"joins\": " + listJson(result.joins) + ",\n" +
            "      \"parseError\": " + json(result.parseError) + ",\n" +
            "      \"query\": " + json(result.query) + "\n" +
            "    }";
    }

    private String mapJson(Map<String, Long> map) {
        return map.entrySet().stream()
            .map(entry -> json(entry.getKey()) + ": " + entry.getValue())
            .collect(Collectors.joining(", ", "{ ", " }"));
    }

    private String listJson(List<String> values) {
        return values.stream().map(this::json).collect(Collectors.joining(", ", "[", "]"));
    }

    private String json(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t") + "\"";
    }

    private String csv(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    private String metric(String label, String value) {
        return "<div class=\"metric\"><div style=\"font-size:12px;color:#5f6b7a;\">" + escapeHtml(label)
            + "</div><div style=\"font-size:28px;font-weight:700;margin-top:8px;\">" + escapeHtml(value) + "</div></div>";
    }

    private String pill(String value) {
        return "<span class=\"pill\">" + escapeHtml(value) + "</span>";
    }

    private String joinPills(List<String> values) {
        if (values.isEmpty()) {
            return "";
        }
        return values.stream().map(this::pill).collect(Collectors.joining());
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
