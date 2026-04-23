package com.migration.scanner.scanner;

import com.migration.scanner.config.Config;
import com.migration.scanner.extractor.JavaExtractor;
import com.migration.scanner.extractor.XmlExtractor;
import com.migration.scanner.model.Result;
import com.migration.scanner.model.ScanReport;
import com.migration.scanner.model.Summary;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ScannerEngine {
    private final Config config;
    private final Set<String> seenQueries = ConcurrentHashMap.newKeySet();

    public ScannerEngine(Config config) {
        this.config = config;
    }

    public ScanReport scan() throws Exception {
        List<Path> files = FileWalker.collect(config.root);
        ExecutorService pool = Executors.newFixedThreadPool(config.threads);
        List<Future<List<Result>>> futures = new ArrayList<>();

        for (Path file : files) {
            futures.add(pool.submit(new FileTask(file)));
        }

        List<Result> results = new ArrayList<>();
        for (Future<List<Result>> future : futures) {
            for (Result result : future.get()) {
                if (!config.dedupe || seenQueries.add(result.normalizedQuery)) {
                    results.add(result);
                }
            }
        }

        pool.shutdown();
        if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
            pool.shutdownNow();
        }
        results.sort(Comparator.comparing((Result r) -> r.path).thenComparingInt(r -> r.line));
        return new ScanReport(results, Summary.from(results));
    }

    final class FileTask implements Callable<List<Result>> {
        private final Path file;

        FileTask(Path file) {
            this.file = file;
        }

        @Override
        public List<Result> call() {
            try {
                String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (fileName.endsWith(".java")) {
                    return JavaExtractor.extract(file);
                }
                if (fileName.endsWith(".xml")) {
                    return XmlExtractor.extract(file);
                }
            } catch (Exception ex) {
                System.err.println("Failed to scan " + file + ": " + ex.getMessage());
            }
            return List.of();
        }
    }
}
