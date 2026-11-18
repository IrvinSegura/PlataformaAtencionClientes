package com.irvinsegura.supportchat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every table is read and written by one module only, so no query joins another module's tables: a module that
 * needs another's data asks its API or keeps its own copy.
 */
class TableOwnershipTests {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Path MODULES = Path.of("src/main/java/com/irvinsegura/supportchat");
    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?i)CREATE\\s+(?:UNLOGGED\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)");

    @Test
    void everyTableIsUsedByOneModuleOnly() throws IOException {
        Set<String> tables = tables();
        Map<String, Set<String>> modulesByTable = new TreeMap<>();
        try (Stream<Path> modules = Files.list(MODULES)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                String code = code(module);
                for (String table : tables) {
                    if (usesTable(code, table)) {
                        modulesByTable.computeIfAbsent(table, t -> new TreeSet<>())
                                .add(module.getFileName().toString());
                    }
                }
            }
        }

        assertThat(tables).hasSizeGreaterThan(10);
        assertThat(modulesByTable).allSatisfy((table, modules) -> assertThat(modules)
                .as("modules using table %s", table)
                .hasSize(1));
    }

    private static Set<String> tables() throws IOException {
        Set<String> tables = new TreeSet<>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                Matcher matcher = CREATE_TABLE.matcher(Files.readString(file));
                while (matcher.find()) {
                    tables.add(matcher.group(1).toLowerCase());
                }
            }
        }
        return tables;
    }

    // The module's Java sources without comment lines, so prose such as "read from workspaces" is not taken for SQL
    private static String code(Path module) throws IOException {
        try (Stream<Path> files = Files.walk(module)) {
            return files.filter(f -> f.toString().endsWith(".java"))
                    .flatMap(TableOwnershipTests::lines)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.strip().startsWith("//") && !line.strip().startsWith("*")
                            && !line.strip().startsWith("/*"))
                    .collect(Collectors.joining("\n"));
        }
    }

    private static Stream<String> lines(Path file) {
        try {
            return Files.readAllLines(file).stream();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }

    private static boolean usesTable(String code, String table) {
        return Pattern.compile("(?i)\\b(?:FROM|JOIN|INTO|UPDATE|TABLE)\\s+" + table + "\\b").matcher(code).find();
    }
}
