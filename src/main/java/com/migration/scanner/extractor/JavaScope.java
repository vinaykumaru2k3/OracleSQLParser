package com.migration.scanner.extractor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class JavaScope {
    private final Map<String, String> stringValues = new LinkedHashMap<>();
    private final Map<String, String> builderValues = new LinkedHashMap<>();

    JavaScope copy() {
        JavaScope clone = new JavaScope();
        clone.stringValues.putAll(this.stringValues);
        clone.builderValues.putAll(this.builderValues);
        return clone;
    }

    Optional<String> getString(String name) {
        return Optional.ofNullable(stringValues.get(name));
    }

    Optional<String> getBuilder(String name) {
        return Optional.ofNullable(builderValues.get(name));
    }

    boolean putString(String name, String value) {
        return !Objects.equals(stringValues.put(name, value), value);
    }

    boolean putBuilder(String name, String value) {
        return !Objects.equals(builderValues.put(name, value), value);
    }

    void appendBuilder(String name, String fragment) {
        builderValues.merge(name, fragment, String::concat);
    }
}
