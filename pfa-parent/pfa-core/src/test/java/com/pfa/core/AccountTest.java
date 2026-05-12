package com.pfa.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    @Test
    void createsValidAccount() {
        var account = new Account("savings-usd", "USD Savings", Bank.BHD, AccountKind.SAVINGS, Currency.USD);
        assertEquals("savings-usd", account.id());
        assertEquals("USD Savings", account.displayName());
        assertEquals(Bank.BHD, account.bank());
        assertEquals(AccountKind.SAVINGS, account.kind());
        assertEquals(Currency.USD, account.primaryCurrency());
    }

    @Test
    void creditCardAccount() {
        var account = new Account("visa-dop", "VISA Mi País", Bank.BHD, AccountKind.CREDIT_CARD, Currency.DOP);
        assertEquals(AccountKind.CREDIT_CARD, account.kind());
        assertEquals(Currency.DOP, account.primaryCurrency());
    }

    @Test
    void nullIdThrows() {
        assertThrows(NullPointerException.class, () ->
                new Account(null, "name", Bank.BHD, AccountKind.SAVINGS, Currency.USD));
    }

    @Test
    void nullDisplayNameThrows() {
        assertThrows(NullPointerException.class, () ->
                new Account("id", null, Bank.BHD, AccountKind.SAVINGS, Currency.USD));
    }

    @Test
    void nullBankThrows() {
        assertThrows(NullPointerException.class, () ->
                new Account("id", "name", null, AccountKind.SAVINGS, Currency.USD));
    }

    @Test
    void nullKindThrows() {
        assertThrows(NullPointerException.class, () ->
                new Account("id", "name", Bank.BHD, null, Currency.USD));
    }

    @Test
    void nullCurrencyThrows() {
        assertThrows(NullPointerException.class, () ->
                new Account("id", "name", Bank.BHD, AccountKind.SAVINGS, null));
    }
}
