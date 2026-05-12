package com.pfa.import_;

import java.util.Arrays;
import java.util.List;

/**
 * Detects the most likely delimiter for a CSV file by analyzing consistency
 * of column counts across sample lines.
 */
public final class DelimiterDetector {

    private static final char[] CANDIDATE_DELIMITERS = {',', ';', '\t', '|'};
    private static final int MAX_SAMPLE_LINES = 20;

    private DelimiterDetector() {
    }

    /**
     * Detects the delimiter that produces the most consistent column count
     * across the sample lines of the given CSV content.
     *
     * @param content the raw CSV text content
     * @return the detected delimiter character, defaults to comma if undetermined
     */
    public static char detect(String content) {
        if (content == null || content.isBlank()) {
            return ',';
        }

        List<String> lines = sampleLines(content);
        if (lines.isEmpty()) {
            return ',';
        }

        char bestDelimiter = ',';
        double bestScore = -1;

        for (char delimiter : CANDIDATE_DELIMITERS) {
            double score = scoreDelimiter(lines, delimiter);
            if (score > bestScore) {
                bestScore = score;
                bestDelimiter = delimiter;
            }
        }

        return bestDelimiter;
    }

    /**
     * Scores a delimiter based on consistency of column counts and minimum column count.
     * Higher score = more likely to be the correct delimiter.
     */
    private static double scoreDelimiter(List<String> lines, char delimiter) {
        int[] columnCounts = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            columnCounts[i] = countColumns(lines.get(i), delimiter);
        }

        // A delimiter that produces only 1 column everywhere is not useful
        int maxColumns = Arrays.stream(columnCounts).max().orElse(0);
        if (maxColumns <= 1) {
            return 0;
        }

        // Find the most common column count (mode)
        int modeCount = findMode(columnCounts);

        // Count how many lines match the mode
        long matchingLines = Arrays.stream(columnCounts)
                .filter(c -> c == modeCount)
                .count();

        // Score = consistency ratio * column count bonus
        double consistency = (double) matchingLines / lines.size();
        double columnBonus = Math.min(modeCount, 10) / 10.0; // prefer more columns, up to 10

        return consistency * 10 + columnBonus;
    }

    /**
     * Counts columns in a line for a given delimiter, respecting quoted fields.
     */
    static int countColumns(String line, char delimiter) {
        if (line.isEmpty()) {
            return 0;
        }

        int count = 1;
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                count++;
            }
        }

        return count;
    }

    private static int findMode(int[] values) {
        if (values.length == 0) return 0;

        int[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);

        int mode = sorted[0];
        int maxFreq = 1;
        int currentFreq = 1;

        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] == sorted[i - 1]) {
                currentFreq++;
            } else {
                currentFreq = 1;
            }
            if (currentFreq > maxFreq) {
                maxFreq = currentFreq;
                mode = sorted[i];
            }
        }

        return mode;
    }

    private static List<String> sampleLines(String content) {
        return content.lines()
                .filter(line -> !line.isBlank())
                .limit(MAX_SAMPLE_LINES)
                .toList();
    }
}
