package com.migration.scanner.model;

import java.util.List;

public final class ScanReport {
    public final List<Result> results;
    public final Summary summary;

    public ScanReport(List<Result> results, Summary summary) {
        this.results = results;
        this.summary = summary;
    }
}
