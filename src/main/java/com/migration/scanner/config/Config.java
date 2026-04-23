package com.migration.scanner.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class Config {
    public Path root = Paths.get(".");
    public Path outputDir = Paths.get("reports");
    public int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
    public boolean dedupe = true;

    public static Config fromArgs(String[] args) {
        Config config = new Config();
        int positionalIndex = 0;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || arg.isBlank()) {
                continue;
            }

            if ("--input".equals(arg) || "-i".equals(arg)) {
                if (i + 1 >= args.length || args[i + 1].isBlank()) {
                    throw new IllegalArgumentException("Missing value for " + arg);
                }
                config.root = Paths.get(args[++i]);
                continue;
            }

            if ("--output".equals(arg) || "-o".equals(arg)) {
                if (i + 1 >= args.length || args[i + 1].isBlank()) {
                    throw new IllegalArgumentException("Missing value for " + arg);
                }
                config.outputDir = Paths.get(args[++i]);
                continue;
            }

            if ("--help".equals(arg) || "-h".equals(arg)) {
                printUsageAndExit();
            }

            if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }

            if (positionalIndex == 0) {
                config.root = Paths.get(arg);
            } else if (positionalIndex == 1) {
                config.outputDir = Paths.get(arg);
            } else {
                throw new IllegalArgumentException("Too many positional arguments.");
            }
            positionalIndex++;
        }
        return config;
    }

    private static void printUsageAndExit() {
        System.out.println("Usage:");
        System.out.println("  mvn compile exec:java \"-Dexec.args=--input <scan-folder> --output <report-folder>\"");
        System.out.println();
        System.out.println("Short flags:");
        System.out.println("  -i <scan-folder>   Input folder to scan");
        System.out.println("  -o <report-folder> Output folder for CSV/JSON/HTML reports");
        System.out.println();
        System.out.println("Positional arguments still work:");
        System.out.println("  <scan-folder> <report-folder>");
        System.exit(0);
    }
}
