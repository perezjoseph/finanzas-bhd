package com.pfa.import_;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Persists and retrieves column mapping configurations for CSV import reuse.
 * Mappings are stored as simple properties files in a configurable directory.
 */
public class ColumnMappingStore {

    private static final String MAPPING_FILE_EXTENSION = ".csvmap";

    private final Path storageDirectory;

    public ColumnMappingStore(Path storageDirectory) {
        this.storageDirectory = Objects.requireNonNull(storageDirectory, "storageDirectory must not be null");
    }

    /**
     * Saves a column mapping configuration for future reuse.
     *
     * @param mapping the mapping to save
     * @throws IOException if writing fails
     */
    public void save(ColumnMapping mapping) throws IOException {
        Files.createDirectories(storageDirectory);
        Path file = storageDirectory.resolve(sanitizeFilename(mapping.name()) + MAPPING_FILE_EXTENSION);

        Properties props = toProperties(mapping);
        try (Writer writer = Files.newBufferedWriter(file)) {
            props.store(writer, "CSV Column Mapping: " + mapping.name());
        }
    }

    /**
     * Loads a column mapping by name.
     *
     * @param name the mapping name
     * @return the mapping, or empty if not found
     * @throws IOException if reading fails
     */
    public Optional<ColumnMapping> load(String name) throws IOException {
        Path file = storageDirectory.resolve(sanitizeFilename(name) + MAPPING_FILE_EXTENSION);
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            props.load(reader);
        }

        return Optional.of(fromProperties(props));
    }

    /**
     * Lists all saved mapping names.
     *
     * @return list of mapping names
     * @throws IOException if reading the directory fails
     */
    public List<String> listMappings() throws IOException {
        if (!Files.exists(storageDirectory)) {
            return List.of();
        }

        try (var stream = Files.list(storageDirectory)) {
            return stream
                    .filter(p -> p.toString().endsWith(MAPPING_FILE_EXTENSION))
                    .map(p -> {
                        try {
                            Properties props = new Properties();
                            try (Reader reader = Files.newBufferedReader(p)) {
                                props.load(reader);
                            }
                            return props.getProperty("name", p.getFileName().toString());
                        } catch (IOException e) {
                            return p.getFileName().toString().replace(MAPPING_FILE_EXTENSION, "");
                        }
                    })
                    .toList();
        }
    }

    /**
     * Deletes a saved mapping by name.
     *
     * @param name the mapping name
     * @return true if the mapping was deleted, false if it didn't exist
     * @throws IOException if deletion fails
     */
    public boolean delete(String name) throws IOException {
        Path file = storageDirectory.resolve(sanitizeFilename(name) + MAPPING_FILE_EXTENSION);
        return Files.deleteIfExists(file);
    }

    private Properties toProperties(ColumnMapping mapping) {
        Properties props = new Properties();
        props.setProperty("name", mapping.name());
        props.setProperty("dateColumn", String.valueOf(mapping.dateColumn()));
        props.setProperty("descriptionColumn", String.valueOf(mapping.descriptionColumn()));
        props.setProperty("amountColumn", String.valueOf(mapping.amountColumn()));
        props.setProperty("delimiter", String.valueOf(mapping.delimiter()));

        mapping.debitColumn().ifPresent(v -> props.setProperty("debitColumn", String.valueOf(v)));
        mapping.creditColumn().ifPresent(v -> props.setProperty("creditColumn", String.valueOf(v)));
        mapping.currencyColumn().ifPresent(v -> props.setProperty("currencyColumn", String.valueOf(v)));
        mapping.referenceColumn().ifPresent(v -> props.setProperty("referenceColumn", String.valueOf(v)));
        mapping.dateFormat().ifPresent(v -> props.setProperty("dateFormat", v));
        mapping.defaultCurrency().ifPresent(v -> props.setProperty("defaultCurrency", v));

        return props;
    }

    private ColumnMapping fromProperties(Properties props) {
        return new ColumnMapping(
                props.getProperty("name", "unnamed"),
                Integer.parseInt(props.getProperty("dateColumn", "0")),
                Integer.parseInt(props.getProperty("descriptionColumn", "1")),
                Integer.parseInt(props.getProperty("amountColumn", "-1")),
                optionalInt(props, "debitColumn"),
                optionalInt(props, "creditColumn"),
                optionalInt(props, "currencyColumn"),
                optionalInt(props, "referenceColumn"),
                Optional.ofNullable(props.getProperty("dateFormat")),
                Optional.ofNullable(props.getProperty("defaultCurrency")),
                props.getProperty("delimiter", ",").charAt(0)
        );
    }

    private OptionalInt optionalInt(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(value));
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
