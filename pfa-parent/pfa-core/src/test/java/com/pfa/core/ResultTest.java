package com.pfa.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void okHoldsValue() {
        Result<String> result = new Result.Ok<>("hello");
        assertInstanceOf(Result.Ok.class, result);
        assertEquals("hello", ((Result.Ok<String>) result).value());
    }

    @Test
    void okRejectsNullValue() {
        assertThrows(NullPointerException.class, () -> new Result.Ok<>(null));
    }

    @Test
    void errHoldsImportError() {
        ImportError error = new ImportError("test.pdf", ErrorCode.CORRUPTED_FILE, "File is corrupted");
        Result<String> result = new Result.Err<>(error);
        assertInstanceOf(Result.Err.class, result);
        assertEquals(error, ((Result.Err<String>) result).error());
    }

    @Test
    void errRejectsNullError() {
        assertThrows(NullPointerException.class, () -> new Result.Err<>(null));
    }

    @Test
    void partialHoldsValueAndWarnings() {
        List<ImportWarning> warnings = List.of(
                new ImportWarning.BalanceMismatch("Expected 100.00, got 99.98"));
        Result<String> result = new Result.Partial<>("data", warnings);
        assertInstanceOf(Result.Partial.class, result);

        Result.Partial<String> partial = (Result.Partial<String>) result;
        assertEquals("data", partial.value());
        assertEquals(1, partial.warnings().size());
        assertEquals("Expected 100.00, got 99.98",
                ((ImportWarning.BalanceMismatch) partial.warnings().get(0)).details());
    }

    @Test
    void partialRejectsNullValue() {
        List<ImportWarning> emptyWarnings = List.of();
        assertThrows(NullPointerException.class,
                () -> new Result.Partial<>(null, emptyWarnings));
    }

    @Test
    void partialRejectsNullWarnings() {
        assertThrows(NullPointerException.class,
                () -> new Result.Partial<>("value", null));
    }

    @Test
    void partialWarningsListIsDefensivelyCopied() {
        var mutableList = new java.util.ArrayList<ImportWarning>();
        mutableList.add(new ImportWarning.BalanceMismatch("mismatch"));

        Result.Partial<String> partial = new Result.Partial<>("data", mutableList);

        // Mutating the original list should not affect the stored warnings
        mutableList.add(new ImportWarning.LowOcrConfidence(1, 50.0));
        assertEquals(1, partial.warnings().size());
    }

    @Test
    void partialWarningsListIsUnmodifiable() {
        List<ImportWarning> warnings = List.of(new ImportWarning.BalanceMismatch("x"));
        Result.Partial<String> partial = new Result.Partial<>("data", warnings);
        List<ImportWarning> storedWarnings = partial.warnings();
        assertThrows(UnsupportedOperationException.class,
                () -> storedWarnings.add(new ImportWarning.BalanceMismatch("y")));
    }

    @Test
    void sealedInterfaceExhaustivenessWithPatternMatching() {
        Result<Integer> ok = new Result.Ok<>(42);
        Result<Integer> err = new Result.Err<>(
                new ImportError("f.pdf", ErrorCode.PARSE_FAILED, "bad"));
        Result<Integer> partial = new Result.Partial<>(10,
                List.of(new ImportWarning.IncompleteTransaction("tx-1", "date")));

        assertEquals("ok:42", describe(ok));
        assertEquals("err:PARSE_FAILED", describe(err));
        assertEquals("partial:10[1]", describe(partial));
    }

    private String describe(Result<Integer> r) {
        return switch (r) {
            case Result.Ok<Integer> ok -> "ok:" + ok.value();
            case Result.Err<Integer> e -> "err:" + e.error().code();
            case Result.Partial<Integer> p -> "partial:" + p.value() + "[" + p.warnings().size() + "]";
        };
    }
}
