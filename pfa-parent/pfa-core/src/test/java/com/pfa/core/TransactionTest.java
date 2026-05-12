package com.pfa.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private Transaction sampleTransaction() {
        return new Transaction(
                UUID.randomUUID(),
                "acct-001",
                LocalDate.of(2026, 2, 24),
                "MASSY STORES Worthing-BB",
                new Money(new BigDecimal("99.56"), Currency.USD),
                Direction.DEBIT,
                Bank.BHD,
                Optional.of("purchase"),
                Optional.of("Groceries"),
                List.of("supermarket"),
                false,
                Set.of(),
                "abc123hash"
        );
    }

    @Test
    void createsValidTransaction() {
        var tx = sampleTransaction();
        assertEquals("acct-001", tx.accountId());
        assertEquals(LocalDate.of(2026, 2, 24), tx.date());
        assertEquals("MASSY STORES Worthing-BB", tx.description());
        assertEquals(Direction.DEBIT, tx.direction());
        assertEquals(Bank.BHD, tx.bank());
        assertFalse(tx.isInternalTransfer());
        assertTrue(tx.issues().isEmpty());
    }

    @Test
    void tagsAreDefensivelyCopied() {
        var mutableTags = new ArrayList<>(List.of("tag1", "tag2"));
        var tx = new Transaction(
                UUID.randomUUID(), "acct-001", LocalDate.now(), "desc",
                new Money(BigDecimal.TEN, Currency.DOP), Direction.CREDIT, Bank.BHD,
                Optional.empty(), Optional.empty(), mutableTags,
                false, Set.of(), "hash"
        );
        mutableTags.add("tag3");
        assertEquals(2, tx.tags().size(), "Tags should be defensively copied");
    }

    @Test
    void issuesAreDefensivelyCopied() {
        var mutableIssues = new HashSet<>(Set.of(FieldIssue.MissingRequired));
        var tx = new Transaction(
                UUID.randomUUID(), "acct-001", LocalDate.now(), "desc",
                new Money(BigDecimal.TEN, Currency.USD), Direction.DEBIT, Bank.BHD,
                Optional.empty(), Optional.empty(), List.of(),
                false, mutableIssues, "hash"
        );
        mutableIssues.add(FieldIssue.DateParseFailed);
        assertEquals(1, tx.issues().size(), "Issues should be defensively copied");
    }

    @Test
    void nullFieldsThrow() {
        assertThrows(NullPointerException.class, () -> new Transaction(
                null, "acct", LocalDate.now(), "desc",
                new Money(BigDecimal.ONE, Currency.USD), Direction.DEBIT, Bank.BHD,
                Optional.empty(), Optional.empty(), List.of(), false, Set.of(), "hash"
        ));
        assertThrows(NullPointerException.class, () -> new Transaction(
                UUID.randomUUID(), null, LocalDate.now(), "desc",
                new Money(BigDecimal.ONE, Currency.USD), Direction.DEBIT, Bank.BHD,
                Optional.empty(), Optional.empty(), List.of(), false, Set.of(), "hash"
        ));
    }
}
