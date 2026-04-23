package com.migration.scanner.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class FileWalker {
    private FileWalker() {
    }

    public static List<Path> collect(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(FileWalker::isSupported)
                .collect(Collectors.toList());
        }
    }

    private static boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".java") || name.endsWith(".xml");
    }
}
