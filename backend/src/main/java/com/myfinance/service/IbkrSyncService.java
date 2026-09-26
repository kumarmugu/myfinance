package com.myfinance.service;

import com.myfinance.model.Account;
import com.myfinance.model.Asset;
import com.myfinance.model.Owner;
import com.myfinance.model.Transaction;
import com.myfinance.model.enums.AssetType;
import com.myfinance.model.enums.Currency;
import com.myfinance.model.enums.InvestmentPurpose;
import com.myfinance.model.enums.TransactionType;
import com.myfinance.repository.TransactionRepository;
import com.myfinance.service.IbkrTradeParser.ParsedCorporateAction;
import com.myfinance.service.IbkrTradeParser.ParsedTrade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Syncs IBKR trades (from a Flex statement) into MyFinance {@link Transaction}s. Two-phase by design
 * so nothing is silently changed:
 * <ol>
 *   <li><b>preview</b> — classify each trade as NEW, DUPLICATE (already present) or MISMATCH
 *       (present but with different values); returns the plan without writing anything.</li>
 *   <li><b>apply</b> — insert NEW trades and, only for the mismatches the user approved, overwrite
 *       the existing transaction with IBKR's values. Then recompute realized P/L.</li>
 * </ol>
 *
 * <p>Matching precedence for an incoming trade:
 * <ol>
 *   <li>An existing transaction with the same IBKR {@code externalId} (tradeID) → already synced.</li>
 *   <li>Otherwise a "same trade" heuristic: same account+owner+symbol+date+type, quantity within a
 *       small tolerance. If price/fees also match → treat as an already-recorded (hand-entered)
 *       duplicate and adopt it; if they differ → MISMATCH (reported for approval).</li>
 *   <li>Otherwise → NEW.</li>
 * </ol>
 *
 * <p>SELLs flow through {@link TransactionService#create} which creates SoldPositions, so a
 * bought-then-sold position imports as an open BUY plus a closing SELL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IbkrSyncService {

    private final AssetService assetService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final IbkrTradeParser tradeParser;
    private final SaxoTradeParser saxoTradeParser;
    private final TigerStatementParser tigerStatementParser;
    private final StockSplitService stockSplitService;

    private static final BigDecimal QTY_TOL = new BigDecimal("0.0001");
    private static final BigDecimal PRICE_TOL = new BigDecimal("0.01");

    public enum Classification { NEW, DUPLICATE, MISMATCH }

    /** One line in the sync plan: an incoming trade and how it relates to existing data. */
    public record TradePlan(String tradeId, String symbol, String type, BigDecimal quantity,
                            BigDecimal price, String currency, LocalDate tradeDate,
                            Classification classification, Long existingTransactionId,
                            String mismatchDetail) {}

    /** A stock split detected in the imported file. The ratio is unknown (not in the file) — the UI
     *  collects it from the user and applies the split on the same Apply click. {@code alreadyApplied}
     *  is true when the user has already run this exact split (symbol+date), so the UI can lock it and
     *  a re-import can't double-adjust the holding. */
    public record SplitInfo(String symbol, LocalDate date, String description, boolean alreadyApplied) {}

    /** The full preview returned to the UI (nothing has been written yet). */
    public record SyncPreview(List<TradePlan> newTrades, List<TradePlan> duplicates,
                              List<TradePlan> mismatches, List<String> skippedNonStock,
                              List<String> corporateActions, List<String> needsReview,
                              List<SplitInfo> splits) {}

    /** Result of applying a sync. */
    public record SyncResult(int inserted, int updated, int skipped, int assetsCreated) {}

    // ─────────────────────────── preview ───────────────────────────

    /** Preview from a live Flex statement XML. */
    public SyncPreview preview(String xml, Long userId, Account account, Owner owner,
                               LocalDate from, LocalDate to) {
        return previewTrades(tradeParser.parse(xml), userId, account, owner, from, to);
    }

    /** Preview from an uploaded trades file (Saxo XLSX, IBKR Flex XML, or IBKR Transaction History CSV). */
    public SyncPreview previewFile(byte[] content, Long userId, Account account, Owner owner,
                                   LocalDate from, LocalDate to) {
        return previewTrades(parseTradeFile(content), userId, account, owner, from, to);
    }

    /**
     * Auto-detect the uploaded trades file: a Saxo XLSX (ZIP), a Tiger "Activity Statement" CSV, or an
     * IBKR Flex XML / Transaction History CSV.
     */
    private IbkrTradeParser.FlexTrades parseTradeFile(byte[] content) {
        if (SaxoTradeParser.looksLikeXlsx(content)) return saxoTradeParser.parse(content);
        String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
        if (TigerStatementParser.looksLikeTigerStatement(text)) return tigerStatementParser.parse(text);
        return tradeParser.parseFile(content);
    }

    /** Shared preview over already-parsed trades — used by both the live fetch and file upload. */
    public SyncPreview previewTrades(IbkrTradeParser.FlexTrades parsed, Long userId, Account account, Owner owner,
                                     LocalDate from, LocalDate to) {
        List<TradePlan> news = new ArrayList<>();
        List<TradePlan> dups = new ArrayList<>();
        List<TradePlan> mismatches = new ArrayList<>();

        for (ParsedTrade t : withSyntheticIds(parsed.trades(), account, owner)) {
            if (outOfRange(t.tradeDate(), from, to)) continue;
            TradePlan plan = classify(t, userId, account, owner);
            switch (plan.classification()) {
                case NEW -> news.add(plan);
                case DUPLICATE -> dups.add(plan);
                case MISMATCH -> mismatches.add(plan);
            }
        }

        List<String> skipped = new ArrayList<>();
        for (ParsedTrade t : parsed.skipped()) {
            if (outOfRange(t.tradeDate(), from, to)) continue;
            skipped.add(t.symbol() + " " + t.assetCategory() + " " + t.tradeDate());
        }
        List<String> corp = new ArrayList<>();
        List<SplitInfo> splits = new ArrayList<>();
        for (ParsedCorporateAction c : parsed.corporateActions()) {
            corp.add(describe(c));
            // Surface splits as structured data so the UI can collect a ratio and apply them on Apply.
            // Flag ones already applied so the UI can lock them and a re-import can't double-adjust.
            if ("SPLIT".equalsIgnoreCase(c.kind()) && c.symbol() != null) {
                boolean applied = stockSplitService.isApplied(userId, c.symbol(), c.date());
                splits.add(new SplitInfo(c.symbol(), c.date(), c.description(), applied));
            }
        }
        List<String> review = new ArrayList<>();
        for (ParsedCorporateAction c : parsed.needsReview()) review.add(describe(c));

        return new SyncPreview(news, dups, mismatches, skipped, corp, review, splits);
    }

    private TradePlan classify(ParsedTrade t, Long userId, Account account, Owner owner) {
        TransactionType type = t.buy() ? TransactionType.BUY : TransactionType.SELL;

        // 1) Already synced by IBKR tradeID?
        if (t.tradeId() != null) {
            var byId = transactionRepository.findByUserIdAndExternalId(userId, t.tradeId());
            if (byId.isPresent()) {
                return plan(t, type, Classification.DUPLICATE, byId.get().getId(), null);
            }
        }
        // 2) Probable hand-entered match: same account+owner+symbol+date+type, quantity ~equal.
        Transaction match = findLikelyMatch(t, userId, account, owner, type);
        if (match != null) {
            String detail = valueMismatch(t, match);
            if (detail == null) return plan(t, type, Classification.DUPLICATE, match.getId(), null);
            return plan(t, type, Classification.MISMATCH, match.getId(), detail);
        }
        // 3) New.
        return plan(t, type, Classification.NEW, null, null);
    }

    private Transaction findLikelyMatch(ParsedTrade t, Long userId, Account account, Owner owner, TransactionType type) {
        for (Transaction tx : transactionRepository.findByUserIdAndAccountIdOrderByTransactionDateDesc(userId, account.getId())) {
            if (tx.getExternalId() != null) continue;                       // already tied to some IBKR trade
            if (tx.getOwner() == null || !owner.getId().equals(tx.getOwner().getId())) continue;
            if (tx.getTransactionType() != type) continue;
            if (!t.tradeDate().equals(tx.getTransactionDate())) continue;
            if (tx.getAsset() == null || tx.getAsset().getSymbol() == null) continue;
            if (!t.symbol().equalsIgnoreCase(tx.getAsset().getSymbol())) continue;
            if (tx.getQuantity() == null || t.quantity().subtract(tx.getQuantity()).abs().compareTo(QTY_TOL) > 0) continue;
            return tx;
        }
        return null;
    }

    /** Returns a human-readable mismatch description, or null when values match within tolerance. */
    private String valueMismatch(ParsedTrade t, Transaction tx) {
        List<String> diffs = new ArrayList<>();
        if (tx.getPricePerUnit() == null || t.price().subtract(tx.getPricePerUnit()).abs().compareTo(PRICE_TOL) > 0) {
            diffs.add("price " + plain(tx.getPricePerUnit()) + " → " + plain(t.price()));
        }
        String txCcy = tx.getCurrency() == null ? null : tx.getCurrency().name();
        if (txCcy != null && !txCcy.equalsIgnoreCase(t.currency())) {
            diffs.add("currency " + txCcy + " → " + t.currency());
        }
        BigDecimal txFee = tx.getFees() == null ? BigDecimal.ZERO : tx.getFees();
        BigDecimal tFee = t.commission() == null ? BigDecimal.ZERO : t.commission();
        if (tFee.subtract(txFee).abs().compareTo(PRICE_TOL) > 0) {
            diffs.add("fees " + plain(txFee) + " → " + plain(tFee));
        }
        return diffs.isEmpty() ? null : String.join(", ", diffs);
    }

    // ─────────────────────────── apply ───────────────────────────

    /**
     * Apply the sync: insert NEW trades, and overwrite ONLY the mismatched transactions whose
     * tradeIds are in {@code approvedMismatchTradeIds}. Duplicates are left untouched (but a
     * hand-entered duplicate that matches is stamped with its IBKR tradeID so future syncs skip it).
     */
    @Transactional
    public SyncResult apply(String xml, Long userId, Account account, Owner owner,
                            LocalDate from, LocalDate to, Set<String> approvedMismatchTradeIds) {
        return applyTrades(tradeParser.parse(xml), userId, account, owner, from, to, approvedMismatchTradeIds);
    }

    /** Apply from an uploaded trades file (Saxo XLSX, IBKR Flex XML, or IBKR Transaction History CSV). */
    @Transactional
    public SyncResult applyFile(byte[] content, Long userId, Account account, Owner owner,
                                LocalDate from, LocalDate to, Set<String> approvedMismatchTradeIds) {
        return applyTrades(parseTradeFile(content), userId, account, owner, from, to, approvedMismatchTradeIds);
    }

    /** Shared apply over already-parsed trades — used by both the live fetch and file upload. */
    @Transactional
    public SyncResult applyTrades(IbkrTradeParser.FlexTrades parsed, Long userId, Account account, Owner owner,
                                  LocalDate from, LocalDate to, Set<String> approvedMismatchTradeIds) {
        int inserted = 0, updated = 0, skipped = 0;
        int[] assetsCreated = {0};

        // Apply chronologically so a position is opened (BUY) before it is closed (SELL). Statements
        // (e.g. Tiger) may list trades grouped by symbol rather than in time order, which would
        // otherwise make a SELL hit an empty holding ("Cannot sell more than held"). Ties on the same
        // date order BUY before SELL for the same reason.
        List<ParsedTrade> ordered = new ArrayList<>(withSyntheticIds(parsed.trades(), account, owner));
        ordered.sort(java.util.Comparator
                .comparing(ParsedTrade::tradeDate, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(t -> t.buy() ? 0 : 1));

        for (ParsedTrade t : ordered) {
            if (outOfRange(t.tradeDate(), from, to)) continue;
            TransactionType type = t.buy() ? TransactionType.BUY : TransactionType.SELL;
            TradePlan plan = classify(t, userId, account, owner);

            switch (plan.classification()) {
                case DUPLICATE -> {
                    // If it's a hand-entered match (no externalId yet), adopt it so re-syncs skip it.
                    if (plan.existingTransactionId() != null && t.tradeId() != null) {
                        transactionRepository.findById(plan.existingTransactionId()).ifPresent(tx -> {
                            if (tx.getExternalId() == null) { tx.setExternalId(t.tradeId()); transactionRepository.save(tx); }
                        });
                    }
                    skipped++;
                }
                case MISMATCH -> {
                    if (t.tradeId() != null && approvedMismatchTradeIds.contains(t.tradeId())
                            && plan.existingTransactionId() != null) {
                        overwrite(plan.existingTransactionId(), t);
                        updated++;
                    } else {
                        skipped++; // not approved → leave the user's data as-is
                    }
                }
                case NEW -> {
                    insertTrade(t, account, owner, type, assetsCreated);
                    inserted++;
                }
            }
        }

        // Keep holdings / sold positions / realized P/L consistent after the writes.
        transactionService.recomputeRealizedPnlForUser(userId);
        log.info("IBKR trade sync for userId={}: inserted={} updated={} skipped={} assetsCreated={}",
                userId, inserted, updated, skipped, assetsCreated[0]);
        return new SyncResult(inserted, updated, skipped, assetsCreated[0]);
    }

    private void insertTrade(ParsedTrade t, Account account, Owner owner, TransactionType type, int[] assetsCreated) {
        Asset asset = findOrCreateAsset(t.symbol(), t.currency(), owner, assetsCreated);
        Transaction tx = transactionService.create(
                asset.getId(), account.getId(), owner.getId(), type,
                t.quantity(), t.price(), t.commission(), t.currency(),
                t.tradeDate(), "Synced from IBKR", InvestmentPurpose.LONG_TERM,
                null, t.fxRateToBase());
        if (t.tradeId() != null) {
            tx.setExternalId(t.tradeId());
            transactionRepository.save(tx);
        }
    }

    private void overwrite(Long txId, ParsedTrade t) {
        transactionRepository.findById(txId).ifPresent(tx -> {
            tx.setQuantity(t.quantity());
            tx.setPricePerUnit(t.price());
            tx.setFees(t.commission() == null ? BigDecimal.ZERO : t.commission());
            tx.setCurrency(parseCurrency(t.currency()));
            tx.setFxRateToBase(t.fxRateToBase());
            tx.setTotalAmount(t.quantity().multiply(t.price())
                    .add(t.commission() == null ? BigDecimal.ZERO : t.commission()));
            if (t.tradeId() != null) tx.setExternalId(t.tradeId());
            transactionRepository.save(tx);
        });
    }

    private Asset findOrCreateAsset(String symbol, String currency, Owner owner, int[] createdCounter) {
        String sym = symbol.trim().toUpperCase();
        return assetService.getBySymbol(sym).orElseGet(() -> {
            Asset a = Asset.builder()
                    .userId(owner.getUserId())
                    .name(sym).symbol(sym)
                    .assetType(AssetType.OTHER)
                    .currency(parseCurrency(currency))
                    .build();
            Asset saved = assetService.create(a);
            createdCounter[0]++;
            return saved;
        });
    }

    private Currency parseCurrency(String code) {
        if (code == null) return Currency.USD;
        try { return Currency.valueOf(code.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return Currency.USD; }
    }

    private boolean outOfRange(LocalDate d, LocalDate from, LocalDate to) {
        if (d == null) return true;
        if (from != null && d.isBefore(from)) return true;
        if (to != null && d.isAfter(to)) return true;
        return false;
    }

    /**
     * Ensure every trade carries a stable id for exact de-duplication. Broker files that lack a real
     * trade id (the IBKR "Transaction History" CSV, Tiger/Saxo statements) otherwise rely on a fuzzy
     * match that can miss on re-import and create duplicates. Here we assign each such trade a
     * deterministic synthetic id derived from its identifying fields (account+owner+symbol+date+type+
     * quantity+price), with an occurrence suffix so genuinely-identical fills in the same file each get
     * a distinct, repeatable id. Re-importing the same file yields the same ids → all classified as
     * DUPLICATE. Trades that already have a real broker id (Flex XML, Tiger API) are left untouched.
     */
    private List<ParsedTrade> withSyntheticIds(List<ParsedTrade> trades, Account account, Owner owner) {
        List<ParsedTrade> out = new ArrayList<>(trades.size());
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        for (ParsedTrade t : trades) {
            if (t.tradeId() != null && !t.tradeId().isBlank()) { out.add(t); continue; }
            String base = "FILE|" + account.getId() + "|" + owner.getId() + "|"
                    + safe(t.symbol()) + "|" + t.tradeDate() + "|" + (t.buy() ? "BUY" : "SELL") + "|"
                    + plain(t.quantity()) + "|" + plain(t.price());
            int occ = seen.merge(base, 1, Integer::sum);
            String synthetic = "FILE-" + Integer.toHexString(base.hashCode()) + "-" + occ;
            out.add(new ParsedTrade(synthetic, t.symbol(), t.assetCategory(), t.buy(), t.quantity(),
                    t.price(), t.currency(), t.tradeDate(), t.commission(), t.fxRateToBase()));
        }
        return out;
    }

    private String safe(String s) { return s == null ? "" : s.trim().toUpperCase(); }

    private TradePlan plan(ParsedTrade t, TransactionType type, Classification c, Long existingId, String detail) {
        return new TradePlan(t.tradeId(), t.symbol(), type.name(), t.quantity(), t.price(),
                t.currency(), t.tradeDate(), c, existingId, detail);
    }

    private String describe(ParsedCorporateAction c) {
        return c.kind() + " " + c.symbol()
                + (c.newSymbol() != null ? " → " + c.newSymbol() : "")
                + (c.ratio() != null ? " (" + plain(c.ratio()) + ")" : "")
                + (c.date() != null ? " on " + c.date() : "")
                + (c.description() != null ? " — " + c.description() : "");
    }

    private String plain(BigDecimal b) {
        return b == null ? "0" : b.stripTrailingZeros().toPlainString();
    }
}
