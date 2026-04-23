package com.migration.scanner;

import com.migration.scanner.config.Config;
import com.migration.scanner.model.ScanReport;
import com.migration.scanner.report.Exporter;
import com.migration.scanner.report.SummaryPrinter;
import com.migration.scanner.scanner.ScannerEngine;

public class OracleQueryScannerApp {
    public static void main(String[] args) throws Exception {
        Config config = Config.fromArgs(args);
        long startMs = System.currentTimeMillis();

        System.out.println("Root: " + config.root.toAbsolutePath());
        System.out.println("Output: " + config.outputDir.toAbsolutePath());

        ScannerEngine engine = new ScannerEngine(config);
        ScanReport report = engine.scan();

        Exporter exporter = new Exporter(config.outputDir);
        exporter.writeCsv(report.results);
        exporter.writeJson(report.results, report.summary);
        exporter.writeHtml(report.results, report.summary);

        SummaryPrinter.print(report.summary, System.currentTimeMillis() - startMs);
    }
}
