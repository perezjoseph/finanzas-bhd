package com.pfa.persistence;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.pfa.core.Account;
import com.pfa.core.AccountKind;
import com.pfa.core.Bank;
import com.pfa.core.Budget;
import com.pfa.core.BudgetPeriod;
import com.pfa.core.Category;
import com.pfa.core.CategoryRule;
import com.pfa.core.Currency;
import com.pfa.core.Direction;
import com.pfa.core.ExchangeRate;
import com.pfa.core.FieldIssue;
import com.pfa.core.Money;
import com.pfa.core.SessionSnapshot;
import com.pfa.core.Transaction;

/**
 * Handles JSON serialization and deserialization of SessionSnapshot using Gson.
 * Includes custom type adapters for Java records, Optional, LocalDate, Instant,
 * UUID, BigDecimal, and sealed interfaces (BudgetPeriod).
 */
class SnapshotSerializer {

    private final Gson gson;

    SnapshotSerializer() {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .registerTypeAdapter(UUID.class, new UuidAdapter())
                .registerTypeAdapter(BigDecimal.class, new BigDecimalAdapter())
                .registerTypeAdapter(Optional.class, new OptionalAdapter())
                .registerTypeAdapter(BudgetPeriod.class, new BudgetPeriodAdapter())
                .registerTypeAdapter(BudgetPeriod.Monthly.class, new BudgetPeriodAdapter())
                .registerTypeAdapter(BudgetPeriod.Custom.class, new BudgetPeriodAdapter())
                .registerTypeAdapter(Transaction.class, new TransactionAdapter())
                .registerTypeAdapter(Money.class, new MoneyAdapter())
                .registerTypeAdapter(ExchangeRate.class, new ExchangeRateAdapter())
                .registerTypeAdapter(Budget.class, new BudgetAdapter())
                .registerTypeAdapter(Account.class, new AccountAdapter())
                .setPrettyPrinting()
                .create();
    }

    /**
     * Serializes a SessionSnapshot to UTF-8 JSON bytes.
     */
    byte[] serialize(SessionSnapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", snapshot.schemaVersion());
        root.add("accounts", gson.toJsonTree(snapshot.accounts()));
        root.add("transactions", gson.toJsonTree(snapshot.transactions()));
        root.add("categories", gson.toJsonTree(snapshot.categories()));
        root.add("learnedRules", gson.toJsonTree(snapshot.learnedRules()));
        root.add("rates", gson.toJsonTree(snapshot.rates()));
        root.add("budgets", gson.toJsonTree(snapshot.budgets()));
        root.add("settings", gson.toJsonTree(snapshot.settings()));
        return gson.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deserializes UTF-8 JSON bytes into a SessionSnapshot.
     *
     * @throws IOException if the data is not valid JSON or is missing required fields
     */
    SessionSnapshot deserialize(byte[] data) throws IOException {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root == null) {
                throw new IOException("Session file is empty or not valid JSON");
            }

            String schemaVersion = getRequiredString(root, "schemaVersion");

            Type accountListType = new TypeToken<List<Account>>() {}.getType();
            Type transactionListType = new TypeToken<List<Transaction>>() {}.getType();
            Type categoryListType = new TypeToken<List<Category>>() {}.getType();
            Type ruleListType = new TypeToken<List<CategoryRule>>() {}.getType();
            Type rateListType = new TypeToken<List<ExchangeRate>>() {}.getType();
            Type budgetListType = new TypeToken<List<Budget>>() {}.getType();
            Type settingsType = new TypeToken<Map<String, String>>() {}.getType();

            List<Account> accounts = deserializeList(root, "accounts", accountListType);
            List<Transaction> transactions = deserializeList(root, "transactions", transactionListType);
            List<Category> categories = deserializeList(root, "categories", categoryListType);
            List<CategoryRule> learnedRules = deserializeList(root, "learnedRules", ruleListType);
            List<ExchangeRate> rates = deserializeList(root, "rates", rateListType);
            List<Budget> budgets = deserializeList(root, "budgets", budgetListType);
            Map<String, String> settings = root.has("settings") && !root.get("settings").isJsonNull()
                    ? gson.fromJson(root.get("settings"), settingsType)
                    : Map.of();

            return new SessionSnapshot(
                    schemaVersion, accounts, transactions, categories,
                    learnedRules, rates, budgets, settings);
        } catch (JsonParseException e) {
            throw new IOException("Failed to parse session JSON: " + e.getMessage(), e);
        }
    }

    private String getRequiredString(JsonObject obj, String field) throws IOException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new IOException("Missing required field: " + field);
        }
        return obj.get(field).getAsString();
    }

    private <T> List<T> deserializeList(JsonObject root, String field, Type type) {
        if (!root.has(field) || root.get(field).isJsonNull()) {
            return List.of();
        }
        List<T> result = gson.fromJson(root.get(field), type);
        return result != null ? result : List.of();
    }

    // --- Type Adapters ---

    private static class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        @Override
        public JsonElement serialize(LocalDate src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        @Override
        public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (json.isJsonNull()) return null;
            return LocalDate.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    private static class InstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.toString());
        }

        @Override
        public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (json.isJsonNull()) return null;
            return Instant.parse(json.getAsString());
        }
    }

    private static class UuidAdapter implements JsonSerializer<UUID>, JsonDeserializer<UUID> {
        @Override
        public JsonElement serialize(UUID src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.toString());
        }

        @Override
        public UUID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (json.isJsonNull()) return null;
            return UUID.fromString(json.getAsString());
        }
    }

    private static class BigDecimalAdapter implements JsonSerializer<BigDecimal>, JsonDeserializer<BigDecimal> {
        @Override
        public JsonElement serialize(BigDecimal src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.toPlainString());
        }

        @Override
        public BigDecimal deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (json.isJsonNull()) return null;
            return new BigDecimal(json.getAsString());
        }
    }

    @SuppressWarnings("rawtypes")
    private static class OptionalAdapter implements JsonSerializer<Optional>, JsonDeserializer<Optional> {
        @Override
        public JsonElement serialize(Optional src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null || src.isEmpty() ? JsonNull.INSTANCE : context.serialize(src.get());
        }

        @Override
        public Optional deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (json == null || json.isJsonNull()) return Optional.empty();
            // Deserialize as String since our Optionals contain Strings
            return Optional.of(json.getAsString());
        }
    }

    private static class BudgetPeriodAdapter
            implements JsonSerializer<BudgetPeriod>, JsonDeserializer<BudgetPeriod> {
        @Override
        public JsonElement serialize(BudgetPeriod src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            switch (src) {
                case BudgetPeriod.Monthly m -> obj.addProperty("type", "Monthly");
                case BudgetPeriod.Custom c -> {
                    obj.addProperty("type", "Custom");
                    obj.addProperty("start", c.start().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    obj.addProperty("end", c.end().format(DateTimeFormatter.ISO_LOCAL_DATE));
                }
            }
            return obj;
        }

        @Override
        public BudgetPeriod deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String type = obj.get("type").getAsString();
            return switch (type) {
                case "Monthly" -> new BudgetPeriod.Monthly();
                case "Custom" -> new BudgetPeriod.Custom(
                        LocalDate.parse(obj.get("start").getAsString(), DateTimeFormatter.ISO_LOCAL_DATE),
                        LocalDate.parse(obj.get("end").getAsString(), DateTimeFormatter.ISO_LOCAL_DATE));
                default -> throw new JsonParseException("Unknown BudgetPeriod type: " + type);
            };
        }
    }

    private static class MoneyAdapter implements JsonSerializer<Money>, JsonDeserializer<Money> {
        @Override
        public JsonElement serialize(Money src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("amount", src.amount().toPlainString());
            obj.addProperty("currency", src.currency().name());
            return obj;
        }

        @Override
        public Money deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            BigDecimal amount = new BigDecimal(obj.get("amount").getAsString());
            Currency currency = Currency.valueOf(obj.get("currency").getAsString());
            return new Money(amount, currency);
        }
    }

    private static class TransactionAdapter
            implements JsonSerializer<Transaction>, JsonDeserializer<Transaction> {
        @Override
        public JsonElement serialize(Transaction tx, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", tx.id().toString());
            obj.addProperty("accountId", tx.accountId());
            obj.addProperty("date", tx.date().format(DateTimeFormatter.ISO_LOCAL_DATE));
            obj.addProperty("description", tx.description());
            obj.add("amount", context.serialize(tx.amount(), Money.class));
            obj.addProperty("direction", tx.direction().name());
            obj.addProperty("bank", tx.bank().name());
            obj.add("transactionType", tx.transactionType().isPresent()
                    ? new JsonPrimitive(tx.transactionType().get()) : JsonNull.INSTANCE);
            obj.add("category", tx.category().isPresent()
                    ? new JsonPrimitive(tx.category().get()) : JsonNull.INSTANCE);
            JsonArray tagsArr = new JsonArray();
            tx.tags().forEach(tagsArr::add);
            obj.add("tags", tagsArr);
            obj.addProperty("isInternalTransfer", tx.isInternalTransfer());
            JsonArray issuesArr = new JsonArray();
            tx.issues().forEach(i -> issuesArr.add(i.name()));
            obj.add("issues", issuesArr);
            obj.addProperty("sourceFileHash", tx.sourceFileHash());
            return obj;
        }

        @Override
        public Transaction deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            UUID id = UUID.fromString(obj.get("id").getAsString());
            String accountId = obj.get("accountId").getAsString();
            LocalDate date = LocalDate.parse(obj.get("date").getAsString(), DateTimeFormatter.ISO_LOCAL_DATE);
            String description = obj.get("description").getAsString();
            Money amount = context.deserialize(obj.get("amount"), Money.class);
            Direction direction = Direction.valueOf(obj.get("direction").getAsString());
            Bank bank = Bank.valueOf(obj.get("bank").getAsString());

            Optional<String> transactionType = obj.has("transactionType") && !obj.get("transactionType").isJsonNull()
                    ? Optional.of(obj.get("transactionType").getAsString())
                    : Optional.empty();
            Optional<String> category = obj.has("category") && !obj.get("category").isJsonNull()
                    ? Optional.of(obj.get("category").getAsString())
                    : Optional.empty();

            List<String> tags = List.of();
            if (obj.has("tags") && !obj.get("tags").isJsonNull()) {
                JsonArray tagsArr = obj.getAsJsonArray("tags");
                tags = new java.util.ArrayList<>();
                for (JsonElement el : tagsArr) {
                    tags.add(el.getAsString());
                }
                tags = List.copyOf(tags);
            }

            boolean isInternalTransfer = obj.has("isInternalTransfer") && obj.get("isInternalTransfer").getAsBoolean();

            Set<FieldIssue> issues = Set.of();
            if (obj.has("issues") && !obj.get("issues").isJsonNull()) {
                JsonArray issuesArr = obj.getAsJsonArray("issues");
                java.util.Set<FieldIssue> issueSet = new java.util.HashSet<>();
                for (JsonElement el : issuesArr) {
                    issueSet.add(FieldIssue.valueOf(el.getAsString()));
                }
                issues = Set.copyOf(issueSet);
            }

            String sourceFileHash = obj.has("sourceFileHash") && !obj.get("sourceFileHash").isJsonNull()
                    ? obj.get("sourceFileHash").getAsString() : "";

            return new Transaction(id, accountId, date, description, amount, direction, bank,
                    transactionType, category, tags, isInternalTransfer, issues, sourceFileHash);
        }
    }

    private static class ExchangeRateAdapter
            implements JsonSerializer<ExchangeRate>, JsonDeserializer<ExchangeRate> {
        @Override
        public JsonElement serialize(ExchangeRate src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("from", src.from().name());
            obj.addProperty("to", src.to().name());
            obj.addProperty("rate", src.rate().toPlainString());
            obj.addProperty("updatedAt", src.updatedAt().toString());
            return obj;
        }

        @Override
        public ExchangeRate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            Currency from = Currency.valueOf(obj.get("from").getAsString());
            Currency to = Currency.valueOf(obj.get("to").getAsString());
            BigDecimal rate = new BigDecimal(obj.get("rate").getAsString());
            Instant updatedAt = Instant.parse(obj.get("updatedAt").getAsString());
            return new ExchangeRate(from, to, rate, updatedAt);
        }
    }

    private static class BudgetAdapter implements JsonSerializer<Budget>, JsonDeserializer<Budget> {
        @Override
        public JsonElement serialize(Budget src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", src.id().toString());
            obj.addProperty("categoryName", src.categoryName());
            obj.add("limit", context.serialize(src.limit(), Money.class));
            obj.add("period", context.serialize(src.period(), BudgetPeriod.class));
            return obj;
        }

        @Override
        public Budget deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            UUID id = UUID.fromString(obj.get("id").getAsString());
            String categoryName = obj.get("categoryName").getAsString();
            Money limit = context.deserialize(obj.get("limit"), Money.class);
            BudgetPeriod period = context.deserialize(obj.get("period"), BudgetPeriod.class);
            return new Budget(id, categoryName, limit, period);
        }
    }

    private static class AccountAdapter implements JsonSerializer<Account>, JsonDeserializer<Account> {
        @Override
        public JsonElement serialize(Account src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", src.id());
            obj.addProperty("displayName", src.displayName());
            obj.addProperty("bank", src.bank().name());
            obj.addProperty("kind", src.kind().name());
            obj.addProperty("primaryCurrency", src.primaryCurrency().name());
            return obj;
        }

        @Override
        public Account deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String id = obj.get("id").getAsString();
            String displayName = obj.get("displayName").getAsString();
            Bank bank = Bank.valueOf(obj.get("bank").getAsString());
            AccountKind kind = AccountKind.valueOf(obj.get("kind").getAsString());
            Currency primaryCurrency = Currency.valueOf(obj.get("primaryCurrency").getAsString());
            return new Account(id, displayName, bank, kind, primaryCurrency);
        }
    }
}
