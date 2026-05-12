package com.pfa.gmail;

import java.util.Properties;

import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.Store;

/**
 * Manual integration test for Gmail IMAP connection.
 * Run with: mvn test -pl pfa-gmail -Dtest=GmailConnectionTest -Dgmail.user=EMAIL -Dgmail.apppassword=PASS
 */
public class GmailConnectionTest {

    public static void main(String[] args) {
        String email = System.getProperty("gmail.user", "");
        String appPassword = System.getProperty("gmail.apppassword", "");

        if (email.isEmpty() || appPassword.isEmpty()) {
            System.out.println("Usage: -Dgmail.user=EMAIL -Dgmail.apppassword=PASS");
            System.out.println("App password should be 16 chars without spaces");
            return;
        }

        // Remove spaces from app password (Google shows them with spaces but they should be entered without)
        appPassword = appPassword.replace(" ", "");

        System.out.println("Testing Gmail IMAP connection...");
        System.out.println("Email: " + email);
        System.out.println("App Password length: " + appPassword.length() + " chars");
        System.out.println();

        // Test 1: imaps protocol with PLAIN auth
        System.out.println("=== Test 1: imaps + PLAIN auth ===");
        testConnection(email, appPassword, createPropsPlain());

        // Test 2: imaps protocol with LOGIN auth
        System.out.println("\n=== Test 2: imaps + LOGIN auth ===");
        testConnection(email, appPassword, createPropsLogin());

        // Test 3: imap + STARTTLS + PLAIN
        System.out.println("\n=== Test 3: imap + ssl.enable + no auth restriction ===");
        testConnection(email, appPassword, createPropsDefault());

        // Test 4: imaps with debug enabled (shows full protocol exchange)
        System.out.println("\n=== Test 4: imaps + debug (full protocol trace) ===");
        testConnectionDebug(email, appPassword);
    }

    private static void testConnection(String email, String password, Properties props) {
        try {
            Session session = Session.getInstance(props);
            String protocol = props.getProperty("mail.store.protocol", "imaps");
            try (Store store = session.getStore(protocol)) {
                System.out.println("  Connecting to imap.gmail.com:993...");
                store.connect("imap.gmail.com", 993, email, password);
                System.out.println("  SUCCESS! Connected.");

                try (Folder inbox = store.getFolder("INBOX")) {
                    inbox.open(Folder.READ_ONLY);
                    System.out.println("  INBOX message count: " + inbox.getMessageCount());
                }
            }
        } catch (jakarta.mail.AuthenticationFailedException e) {
            System.out.println("  FAILED (AUTH): " + e.getMessage());
        } catch (Exception e) {
            System.out.println("  FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void testConnectionDebug(String email, String password) {
        try {
            Properties props = new Properties();
            props.setProperty("mail.store.protocol", "imaps");
            props.setProperty("mail.imaps.host", "imap.gmail.com");
            props.setProperty("mail.imaps.port", "993");
            props.setProperty("mail.imaps.ssl.enable", "true");
            props.setProperty("mail.imaps.auth.mechanisms", "PLAIN");
            props.setProperty("mail.imaps.timeout", "15000");
            props.setProperty("mail.imaps.connectiontimeout", "15000");

            Session session = Session.getInstance(props);
            session.setDebug(true); // This prints the full IMAP protocol exchange
            try (Store store = session.getStore("imaps")) {
                System.out.println("  Connecting with debug...");
                store.connect("imap.gmail.com", 993, email, password);
                System.out.println("  SUCCESS!");
            }
        } catch (jakarta.mail.AuthenticationFailedException e) {
            System.out.println("  FAILED (AUTH): " + e.getMessage());
        } catch (Exception e) {
            System.out.println("  FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static Properties createPropsPlain() {
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imaps");
        props.setProperty("mail.imaps.host", "imap.gmail.com");
        props.setProperty("mail.imaps.port", "993");
        props.setProperty("mail.imaps.ssl.enable", "true");
        props.setProperty("mail.imaps.auth.mechanisms", "PLAIN");
        props.setProperty("mail.imaps.timeout", "15000");
        props.setProperty("mail.imaps.connectiontimeout", "15000");
        return props;
    }

    private static Properties createPropsLogin() {
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imaps");
        props.setProperty("mail.imaps.host", "imap.gmail.com");
        props.setProperty("mail.imaps.port", "993");
        props.setProperty("mail.imaps.ssl.enable", "true");
        props.setProperty("mail.imaps.auth.mechanisms", "LOGIN");
        props.setProperty("mail.imaps.timeout", "15000");
        props.setProperty("mail.imaps.connectiontimeout", "15000");
        return props;
    }

    private static Properties createPropsDefault() {
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imap");
        props.setProperty("mail.imap.host", "imap.gmail.com");
        props.setProperty("mail.imap.port", "993");
        props.setProperty("mail.imap.ssl.enable", "true");
        props.setProperty("mail.imap.timeout", "15000");
        props.setProperty("mail.imap.connectiontimeout", "15000");
        return props;
    }
}
