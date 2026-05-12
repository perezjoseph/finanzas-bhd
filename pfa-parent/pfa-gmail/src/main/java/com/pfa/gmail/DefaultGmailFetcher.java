package com.pfa.gmail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.pfa.core.AccountAssignment;
import com.pfa.core.AccountKind;
import com.pfa.core.Bank;
import com.pfa.core.FetchResult;
import com.pfa.core.FetchRule;
import com.pfa.core.GmailAccount;
import com.pfa.core.GmailFetcher;
import com.pfa.core.ImportSource;

import jakarta.mail.BodyPart;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;

/**
 * Default implementation of GmailFetcher using Jakarta Mail IMAP.
 * Connects to imap.gmail.com:993 with email + App Password.
 */
public class DefaultGmailFetcher implements GmailFetcher {

    private static final String IMAP_HOST = "imap.gmail.com";
    private static final int IMAP_PORT = 993;

    @SuppressWarnings("java:S1854")
    private final Map<String, GmailAccount> savedAccounts = new HashMap<>();

    @Override
    public FetchResult fetch(GmailAccount account, List<FetchRule> rules) {
        List<ImportSource> downloaded = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Properties props = createImapProperties();
        Session session = Session.getInstance(props);

        try (Store store = session.getStore("imaps")) {
            store.connect(IMAP_HOST, IMAP_PORT, account.email(), new String(account.appPassword()));

            try (Folder inbox = store.getFolder("INBOX")) {
                inbox.open(Folder.READ_ONLY);

                for (FetchRule rule : rules) {
                    fetchByRule(inbox, rule, account, downloaded, errors);
                }
            }
        } catch (MessagingException e) {
            errors.add("Connection failed: " + e.getMessage());
        }

        return new FetchResult(downloaded, errors);
    }

    @Override
    public void saveAccount(GmailAccount account) {
        savedAccounts.put(account.email(), account);
    }

    @Override
    public void removeAccount(String email) {
        savedAccounts.remove(email);
    }

    @Override
    public GmailAccount getAccount(String email) {
        return savedAccounts.get(email);
    }

    private void fetchByRule(Folder inbox, FetchRule rule, GmailAccount account,
                             List<ImportSource> downloaded, List<String> errors) {
        try {
            SearchTerm searchTerm = buildSearchTerm(rule);
            Message[] messages = inbox.search(searchTerm);

            for (Message message : messages) {
                extractPdfAttachments(message, account, downloaded, errors);
            }
        } catch (MessagingException e) {
            errors.add("Search failed for rule [" + rule.senderPattern() + "]: " + e.getMessage());
        }
    }

    private void extractPdfAttachments(Message message, GmailAccount account,
                                       List<ImportSource> downloaded, List<String> errors) {
        try {
            Object content = message.getContent();
            if (content instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart part = multipart.getBodyPart(i);
                    if (isPdfAttachment(part)) {
                        byte[] bytes = readAttachment(part);
                        String filename = part.getFileName();
                        AccountAssignment assignment = new AccountAssignment(
                                account.email(), Bank.BHD, AccountKind.SAVINGS);
                        downloaded.add(new ImportSource.GmailAttachment(bytes, filename, assignment));
                    }
                }
            }
        } catch (MessagingException | IOException e) {
            errors.add("Failed to extract attachment: " + e.getMessage());
        }
    }

    private boolean isPdfAttachment(BodyPart part) throws MessagingException {
        String disposition = part.getDisposition();
        if (disposition == null || !Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
            return false;
        }
        String contentType = part.getContentType();
        return contentType != null && contentType.toLowerCase().contains("pdf");
    }

    private byte[] readAttachment(BodyPart part) throws IOException, MessagingException {
        try (InputStream is = part.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }

    private SearchTerm buildSearchTerm(FetchRule rule) {
        SearchTerm fromTerm = new FromStringTerm(rule.senderPattern());
        SearchTerm subjectTerm = new SubjectTerm(rule.subjectPattern());
        return new AndTerm(fromTerm, subjectTerm);
    }

    private Properties createImapProperties() {
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imaps");
        props.setProperty("mail.imaps.host", IMAP_HOST);
        props.setProperty("mail.imaps.port", String.valueOf(IMAP_PORT));
        props.setProperty("mail.imaps.ssl.enable", "true");
        props.setProperty("mail.imaps.timeout", "30000");
        props.setProperty("mail.imaps.connectiontimeout", "15000");
        // Force PLAIN auth — prevents Jakarta Mail from attempting XOAUTH2
        // (which Gmail advertises) with the App Password, causing auth failure.
        props.setProperty("mail.imaps.auth.mechanisms", "PLAIN");
        return props;
    }
}
