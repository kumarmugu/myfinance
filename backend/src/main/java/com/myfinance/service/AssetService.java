package com.myfinance.service;

import com.myfinance.config.ReferenceConstraintException;
import com.myfinance.model.Asset;
import com.myfinance.model.Holding;
import com.myfinance.model.enums.AssetType;
import com.myfinance.repository.AssetRepository;
import com.myfinance.repository.DividendRepository;
import com.myfinance.repository.HoldingRepository;
import com.myfinance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {
    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final HoldingRepository holdingRepository;
    private final DividendRepository dividendRepository;

    public List<Asset> getAll() { return assetRepository.findAll(); }
    public Asset getById(Long id) { return assetRepository.findById(id).orElseThrow(() -> new RuntimeException("Asset not found: " + id)); }
    public Optional<Asset> getBySymbol(String symbol) { return assetRepository.findBySymbol(symbol); }

    /**
     * Find this user's asset that has previously traded under {@code ticker} (recorded in
     * {@link Asset#getPreviousSymbols()} as a comma-separated list). Used so a broker import that
     * still reports an old ticker (e.g. FB) folds into the renamed asset (META) instead of creating
     * a duplicate. Exact symbol matches are handled by {@link #getBySymbol}; this covers aliases only.
     */
    public Optional<Asset> getByPreviousSymbol(Long userId, String ticker) {
        if (userId == null || ticker == null || ticker.isBlank()) return Optional.empty();
        String want = ticker.trim().toUpperCase();
        for (Asset a : assetRepository.findByUserId(userId)) {
            String prev = a.getPreviousSymbols();
            if (prev == null || prev.isBlank()) continue;
            for (String s : prev.split(",")) {
                if (s.trim().equalsIgnoreCase(want)) return Optional.of(a);
            }
        }
        return Optional.empty();
    }
    /** Clean a user-entered previous-symbols CSV: upper-case, trimmed, de-duped, own symbol removed. */
    private String normalizePreviousSymbols(String csv, String ownSymbol) {
        if (csv == null || csv.isBlank()) return null;
        String own = ownSymbol == null ? "" : ownSymbol.trim().toUpperCase();
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String s : csv.split(",")) {
            String v = s.trim().toUpperCase();
            if (!v.isEmpty() && !v.equals(own)) out.add(v);
        }
        return out.isEmpty() ? null : String.join(",", out);
    }

    public List<Asset> getByType(AssetType type) { return assetRepository.findByAssetType(type); }
    public List<Asset> search(String query) { return assetRepository.findByNameContainingIgnoreCaseOrSymbolContainingIgnoreCase(query, query); }
    public Asset create(Asset asset) {
        // Stamp the price date on creation only if a price was actually provided.
        if (asset.getCurrentPrice() != null) asset.setPriceUpdatedAt(java.time.LocalDateTime.now());
        Asset saved = assetRepository.save(asset);
        log.info("Created Asset id={} symbol={}", saved.getId(), saved.getSymbol());
        return saved;
    }
    public Asset update(Long id, Asset updated) {
        Asset existing = getById(id);
        existing.setName(updated.getName());
        existing.setSymbol(updated.getSymbol());
        existing.setAssetType(updated.getAssetType());
        // Only refresh priceUpdatedAt when the price value genuinely changes, so an unrelated
        // edit (e.g. renaming the asset) doesn't make a stale price look freshly updated.
        applyPriceChange(existing, updated.getCurrentPrice());
        boolean currencyChanged = existing.getCurrency() != updated.getCurrency();
        existing.setCurrency(updated.getCurrency());
        existing.setExchange(updated.getExchange());
        existing.setDescription(updated.getDescription());
        // Former tickers (CSV) so a broker import of an old symbol folds into this renamed asset.
        existing.setPreviousSymbols(normalizePreviousSymbols(updated.getPreviousSymbols(), updated.getSymbol()));
        Asset saved = assetRepository.save(existing);
        // The asset's currency is the instrument's source of truth. When it changes, cascade it to
        // every holding of this asset so the Portfolio never shows a holding in a stale currency
        // (the holding stores its own copy, historically seeded from the broker account's default).
        if (currencyChanged && saved.getCurrency() != null) {
            List<Holding> holdings = holdingRepository.findByAssetId(id);
            for (Holding h : holdings) {
                if (h.getCurrency() != saved.getCurrency()) {
                    h.setCurrency(saved.getCurrency());
                    holdingRepository.save(h);
                }
            }
            if (!holdings.isEmpty()) {
                log.info("Cascaded currency {} from Asset id={} to {} holding(s)", saved.getCurrency(), id, holdings.size());
            }
        }
        log.info("Updated Asset id={} symbol={}", id, saved.getSymbol());
        return saved;
    }
    public Asset updatePrice(Long id, BigDecimal price) {
        Asset asset = getById(id);
        applyPriceChange(asset, price);
        return assetRepository.save(asset);
    }

    /** Set the price and stamp priceUpdatedAt only if the value actually differs from the current one. */
    private void applyPriceChange(Asset asset, BigDecimal newPrice) {
        BigDecimal current = asset.getCurrentPrice();
        boolean changed = (current == null)
                ? newPrice != null
                : (newPrice == null || current.compareTo(newPrice) != 0);
        asset.setCurrentPrice(newPrice);
        if (changed && newPrice != null) {
            asset.setPriceUpdatedAt(java.time.LocalDateTime.now());
        }
    }

    public Asset toggleNetWorth(Long id, boolean include) {
        Asset asset = getById(id);
        asset.setIncludeInNetWorth(include);
        return assetRepository.save(asset);
    }

    /** Result of a duplicate-asset merge, so the caller can report what was cleaned up. */
    public record MergeResult(int assetsMerged, int dividendsRepointed, int transactionsRepointed,
                              int holdingsRepointed, int duplicateDividendsRemoved) {}

    /** Outcome of folding one asset into another. */
    public record MergeIntoResult(String survivingSymbol, String mergedSymbol,
                                  int transactionsRepointed, int holdingsMerged, int dividendsRepointed) {}

    /**
     * Fold {@code sourceId} into {@code targetId}: move the source asset's transactions, holdings and
     * dividends onto the target, record the source's ticker (and any it already carried) as a
     * {@code previousSymbol} of the target so future imports of the old ticker resolve to the target,
     * then delete the source. Use when a ticker rename (e.g. FB → META) created a separate asset.
     *
     * <p>Tenant-scoped: both assets must belong to {@code userId}. Holdings for the same
     * account+owner are combined (quantities add, invested adds, average buy price re-derived).
     */
    @org.springframework.transaction.annotation.Transactional
    public MergeIntoResult mergeInto(Long userId, Long sourceId, Long targetId) {
        if (sourceId == null || targetId == null || sourceId.equals(targetId)) {
            throw new RuntimeException("Pick two different assets to merge.");
        }
        Asset source = getById(sourceId);
        Asset target = getById(targetId);
        if (!userId.equals(source.getUserId()) || !userId.equals(target.getUserId())) {
            throw new RuntimeException("You can only merge your own assets.");
        }

        // Repoint transactions and dividends wholesale.
        int txns = 0;
        for (var t : transactionRepository.findByAssetIdOrderByTransactionDateDesc(sourceId)) {
            t.setAsset(target); transactionRepository.save(t); txns++;
        }
        int divs = 0;
        for (var d : dividendRepository.findByAssetId(sourceId)) {
            d.setAsset(target); dividendRepository.save(d); divs++;
        }

        // Holdings: merge into the target's holding for the same account+owner, else repoint.
        int holdingsMerged = 0;
        List<Holding> targetHoldings = holdingRepository.findByAssetId(targetId);
        for (Holding sh : holdingRepository.findByAssetId(sourceId)) {
            Holding tgt = targetHoldings.stream()
                    .filter(th -> th.getAccount() != null && sh.getAccount() != null
                            && th.getAccount().getId().equals(sh.getAccount().getId())
                            && th.getOwner() != null && sh.getOwner() != null
                            && th.getOwner().getId().equals(sh.getOwner().getId()))
                    .findFirst().orElse(null);
            if (tgt == null) {
                sh.setAsset(target);
                holdingRepository.save(sh);
            } else {
                BigDecimal newQty = tgt.getQuantity().add(sh.getQuantity());
                BigDecimal newInvested = tgt.getInvestedAmount().add(sh.getInvestedAmount());
                tgt.setQuantity(newQty);
                tgt.setInvestedAmount(newInvested);
                tgt.setAverageBuyPrice(newQty.signum() == 0 ? BigDecimal.ZERO
                        : newInvested.divide(newQty, 6, java.math.RoundingMode.HALF_UP));
                holdingRepository.save(tgt);
                holdingRepository.deleteById(sh.getId());
            }
            holdingsMerged++;
        }

        // Record the source ticker (and any tickers it already aliased) as previous symbols of target,
        // so a future import of the old ticker folds into the target rather than recreating the source.
        java.util.LinkedHashSet<String> prev = new java.util.LinkedHashSet<>();
        if (target.getPreviousSymbols() != null) {
            for (String s : target.getPreviousSymbols().split(",")) if (!s.isBlank()) prev.add(s.trim().toUpperCase());
        }
        if (source.getSymbol() != null) prev.add(source.getSymbol().trim().toUpperCase());
        if (source.getPreviousSymbols() != null) {
            for (String s : source.getPreviousSymbols().split(",")) if (!s.isBlank()) prev.add(s.trim().toUpperCase());
        }
        prev.remove(target.getSymbol() == null ? "" : target.getSymbol().trim().toUpperCase()); // never alias itself
        target.setPreviousSymbols(prev.isEmpty() ? null : String.join(",", prev));
        assetRepository.save(target);

        assetRepository.deleteById(sourceId);
        log.info("Merged asset id={} symbol='{}' into id={} symbol='{}': {} txns, {} holdings, {} dividends; previousSymbols now '{}'",
                sourceId, source.getSymbol(), targetId, target.getSymbol(), txns, holdingsMerged, divs, target.getPreviousSymbols());

        return new MergeIntoResult(target.getSymbol(), source.getSymbol(), txns, holdingsMerged, divs);
    }

    /**
     * Merge duplicate assets created by imports back into the canonical asset. A duplicate is one
     * whose symbol is a descriptive form containing the real ticker in trailing parentheses (e.g.
     * "META PLATFORMS, INC. (META)") when a bare-ticker asset ("META") already exists for the user.
     *
     * <p>References (dividends, transactions, holdings) are repointed to the canonical asset, then
     * the duplicate is deleted. Only same-user assets are touched. Idempotent.
     */
    @org.springframework.transaction.annotation.Transactional
    public MergeResult mergeDuplicateAssets(Long userId) {
        List<Asset> all = assetRepository.findByUserId(userId);
        // Canonical assets keyed by upper-case symbol.
        java.util.Map<String, Asset> bySymbol = new java.util.HashMap<>();
        for (Asset a : all) if (a.getSymbol() != null) bySymbol.putIfAbsent(a.getSymbol().trim().toUpperCase(), a);

        int merged = 0, divs = 0, txns = 0, holds = 0;
        for (Asset dup : all) {
            String ticker = tickerInParens(dup.getSymbol());
            if (ticker == null) continue;                        // not a "NAME (TICKER)" form
            Asset canonical = bySymbol.get(ticker);
            if (canonical == null || canonical.getId().equals(dup.getId())) continue; // no target / itself
            if (!userId.equals(dup.getUserId()) || !userId.equals(canonical.getUserId())) continue;

            for (var d : dividendRepository.findByAssetId(dup.getId())) { d.setAsset(canonical); dividendRepository.save(d); divs++; }
            for (var t : transactionRepository.findByAssetIdOrderByTransactionDateDesc(dup.getId())) { t.setAsset(canonical); transactionRepository.save(t); txns++; }
            for (var h : holdingRepository.findByAssetId(dup.getId())) { h.setAsset(canonical); holdingRepository.save(h); holds++; }
            assetRepository.deleteById(dup.getId());
            merged++;
            log.info("Merged duplicate asset id={} symbol='{}' into canonical id={} ({})",
                    dup.getId(), dup.getSymbol(), canonical.getId(), ticker);
        }

        int divsRemoved = removeDuplicateDividends(userId);
        return new MergeResult(merged, divs, txns, holds, divsRemoved);
    }

    /**
     * Remove duplicate dividend rows created by importing the same payout twice under different
     * instrument spellings (e.g. "INVESCO QQQ (QQQ)" then "QQQ"). Within a group of rows sharing
     * (asset, account, owner, date, amount), extras are deleted ONLY when they carry a descriptive
     * "NAME (TICKER)" instrument — the import artifact — keeping the plain-ticker row. Groups whose
     * rows all share the same instrument (a genuine same-day double payout) are left untouched.
     */
    private int removeDuplicateDividends(Long userId) {
        var all = dividendRepository.findByUserIdOrderByReceivedDateDesc(userId);
        java.util.Map<String, java.util.List<com.myfinance.model.Dividend>> groups = new java.util.HashMap<>();
        for (var d : all) {
            String key = (d.getAsset() != null ? d.getAsset().getId() : "0") + "|"
                    + (d.getAccount() != null ? d.getAccount().getId() : "0") + "|"
                    + (d.getOwner() != null ? d.getOwner().getId() : "0") + "|"
                    + (d.getReceivedDate() != null ? d.getReceivedDate() : "") + "|"
                    + (d.getAmount() != null ? d.getAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "");
            groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(d);
        }
        int removed = 0;
        for (var group : groups.values()) {
            if (group.size() < 2) continue;
            // Only delete descriptive "NAME (TICKER)" rows, and only when a plain-ticker twin exists
            // to keep. Plain-ticker rows are never deleted, so a genuine same-day double payout (two
            // identical instruments) is preserved intact.
            boolean anyPlain = group.stream().anyMatch(d -> tickerInParens(d.getInstrument()) == null);
            if (!anyPlain) continue; // no bare-ticker survivor → treat as genuine, leave alone
            for (var d : group) {
                if (tickerInParens(d.getInstrument()) != null) { dividendRepository.delete(d); removed++; }
            }
        }
        if (removed > 0) log.info("Removed {} duplicate dividend rows for userId={}", removed, userId);
        return removed;
    }

    /** Return the ticker inside trailing parentheses of a symbol, e.g. "Foo (BAR)" → "BAR", else null. */
    private String tickerInParens(String symbol) {
        if (symbol == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([A-Za-z0-9.\\-]{1,15})\\)\\s*$").matcher(symbol.trim());
        return m.find() ? m.group(1).toUpperCase() : null;
    }

    public void delete(Long id) {
        Asset asset = getById(id);
        List<String> references = new ArrayList<>();

        long txCount = transactionRepository.findByAssetIdOrderByTransactionDateDesc(id).size();
        if (txCount > 0) references.add(txCount + " Transaction(s)");

        long holdingCount = holdingRepository.findByAssetId(id).size();
        if (holdingCount > 0) references.add(holdingCount + " Holding(s)");

        long divCount = dividendRepository.findByAssetId(id).size();
        if (divCount > 0) references.add(divCount + " Dividend(s)");

        if (!references.isEmpty()) {
            log.warn("Cannot delete Asset id={}, referenced by: {}", id, references);
            throw new ReferenceConstraintException("Asset '" + asset.getSymbol() + " - " + asset.getName() + "'", references);
        }

        assetRepository.deleteById(id);
        log.info("Deleted Asset id={}", id);
    }
}
