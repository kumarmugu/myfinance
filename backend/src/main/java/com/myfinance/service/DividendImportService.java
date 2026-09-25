package com.myfinance.service;

import com.myfinance.model.Account;
import com.myfinance.model.Asset;
import com.myfinance.model.Dividend;
import com.myfinance.model.Owner;
import com.myfinance.model.enums.AssetType;
import com.myfinance.model.enums.Currency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parses broker dividend statements (Interactive Brokers "Transaction History" CSV and Saxo
 * "Share Dividends" XLSX) into {@link Dividend} rows and persists them for the current user.
 *
 * <p>Rules (agreed with the product owner):
 * <ul>
 *   <li>Store the NET amount actually received (gross minus withholding tax), in the row's
 *       ORIGINAL currency. Gross and tax are kept in their own fields for reporting.</li>
 *   <li>Only genuine dividend/distribution rows are imported — buys, deposits, withdrawals, FX
 *       trades, interest, fees and adjustments are ignored.</li>
 *   <li>Reversal pairs (a distribution and its later reversal) net to zero and are BOTH skipped.</li>
 *   <li>Missing assets are auto-created (by symbol) so the dividend links to a real instrument.</li>
 * </ul>
 *
 * XLSX is read with the JDK only (a .xlsx is a zip of XML) — no Apache POI dependency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendImportService {

    private final AssetService assetService;
    private final DividendService dividendService;
    private final com.myfinance.repository.DividendRepository dividendRepository;

    /** Outcome of an import run, surfaced to the UI. */
    public record ImportResult(int imported, int skipped, int assetsCreated) {}

    /** A single parsed dividend, before it is linked to an asset and persisted. */
    public record ParsedDividend(LocalDate payDate, String symbol, String currency,
                                 BigDecimal net, BigDecimal gross, BigDecimal tax, String type) {}

    public enum Format { IBKR_CSV, IBKR_FLEX_XML, SAXO_XLSX, TIGER_CSV }

    @Transactional
    public ImportResult importFile(byte[] content, Format format, Long userId, Account account, Owner owner) {
        List<ParsedDividend> parsed = switch (format) {
            case IBKR_CSV -> parseIbkr(new String(content, java.nio.charset.StandardCharsets.UTF_8));
            case IBKR_FLEX_XML -> parseFlexXml(new String(content, java.nio.charset.StandardCharsets.UTF_8));
            case SAXO_XLSX -> parseSaxo(content);
            case TIGER_CSV -> parseTiger(new String(content, java.nio.charset.StandardCharsets.UTF_8));
        };
        String source = switch (format) {
            case IBKR_CSV, IBKR_FLEX_XML -> "IBKR";
            case SAXO_XLSX -> "Saxo";
            case TIGER_CSV -> "Tiger";
        };
        return persist(parsed, userId, account, owner, source);
    }

    /**
     * Persist already-parsed dividend rows for the current user, de-duplicating against what's
     * stored (so re-running is idempotent) and auto-creating any missing assets. Shared by the
     * file importer and the IBKR Flex fetch so both apply identical dedupe/asset rules.
     *
     * @param source short label used in the row's notes (e.g. "IBKR", "IBKR Flex").
     */
    @Transactional
    public ImportResult persist(List<ParsedDividend> parsed, Long userId, Account account, Owner owner, String source) {
        // De-duplicate against what's already stored, so re-importing the same data is idempotent.
        // Key = date | instrument | accountId | ownerId | amount. We use a COUNT multiset so genuine
        // same-day duplicates in a statement (e.g. two identical return-of-capital rows) still import
        // the correct number of times rather than being over-skipped.
        // Index existing dividends by symbol+date+account+owner (NOT amount) so the same event from a
        // different source reconciles instead of duplicating. One dividend per key.
        Map<String, Dividend> existing = new HashMap<>();
        for (Dividend d : dividendRepository.findByUserIdOrderByReceivedDateDesc(userId)) {
            if (d.getAccount() == null || !account.getId().equals(d.getAccount().getId())) continue;
            if (d.getOwner() == null || !owner.getId().equals(d.getOwner().getId())) continue;
            String k = dedupeKey(d.getReceivedDate() == null ? null : d.getReceivedDate().toString(),
                    d.getInstrument(), account.getId(), owner.getId());
            existing.putIfAbsent(k, d); // keep the first (most recent by received date) per key
        }

        int[] assetsCreated = {0};
        int imported = 0, skipped = 0, updated = 0;
        for (ParsedDividend d : parsed) {
            String key = dedupeKey(d.payDate() == null ? null : d.payDate().toString(),
                    d.symbol(), account.getId(), owner.getId());
            Dividend prior = existing.get(key);
            if (prior != null) {
                // Same distribution already recorded. If this source reports a MORE COMPLETE total
                // (larger absolute net — e.g. the CSV's full 83.97 vs Flex's partial 14.85), correct
                // the stored record to the fuller figure; otherwise leave it. Never create a second row.
                // Compare at 2 dp so DB column-scale rounding of the stored amount is not mistaken for
                // a "more complete" figure — only a genuinely larger total (by at least a cent) updates.
                BigDecimal newNet = (d.net() == null ? BigDecimal.ZERO : d.net()).abs().setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal oldNet = (prior.getAmount() == null ? BigDecimal.ZERO : prior.getAmount()).abs().setScale(2, java.math.RoundingMode.HALF_UP);
                if (newNet.compareTo(oldNet) > 0) {
                    prior.setAmount(d.net());
                    prior.setGrossAmount(d.gross());
                    prior.setWithholdingTax(d.tax());
                    if (d.type() != null) prior.setDividendType(d.type());
                    prior.setNotes("Imported from " + source + " statement");
                    dividendService.create(prior); // save() updates the existing row (it has an id)
                    updated++;
                } else {
                    skipped++;
                }
                continue;
            }
            Asset asset = (d.symbol() != null && !d.symbol().isBlank())
                    ? findOrCreateAsset(d.symbol(), d.currency(), userId, assetsCreated)
                    : null;
            Dividend div = Dividend.builder()
                    .userId(userId)
                    .asset(asset)
                    .account(account)
                    .owner(owner)
                    .amount(d.net())
                    .grossAmount(d.gross())
                    .withholdingTax(d.tax())
                    .dividendType(d.type())
                    .currency(parseCurrency(d.currency()))
                    .receivedDate(d.payDate())
                    .year(d.payDate() != null ? d.payDate().getYear() : null)
                    .quarter(d.payDate() != null ? "Q" + ((d.payDate().getMonthValue() - 1) / 3 + 1) : null)
                    .instrument(d.symbol())
                    .notes("Imported from " + source + " statement")
                    .build();
            Dividend saved = dividendService.create(div);
            existing.put(key, saved); // so a later row in the same file for this key reconciles too
            imported++;
        }
        log.info("Dividend import ({}) for userId={}: imported={} updated={} skipped={} assetsCreated={}",
                source, userId, imported, updated, skipped, assetsCreated[0]);
        // "skipped" reports rows that did not create a new dividend — both exact duplicates and rows
        // that reconciled/updated an existing one (an update is not a newly-imported record).
        return new ImportResult(imported, skipped + updated, assetsCreated[0]);
    }

    /**
     * Normalized dedupe key: date | instrument-ticker | account | owner. The amount is deliberately
     * NOT part of the key. An instrument pays at most one distribution per pay-date into one account,
     * so the same event imported from different sources (IBKR live Flex vs the IBKR CSV, which report
     * REIT components differently and can total differently) must be recognised as the SAME dividend
     * and not duplicated just because the two totals differ. The instrument is reduced to its ticker
     * so a descriptive form ("INVESCO QQQ (QQQ)") and the bare ticker ("QQQ") collide and dedupe.
     */
    private String dedupeKey(String date, String instrument, Long accountId, Long ownerId) {
        String inst = instrument == null ? "" : (extractTicker(instrument) == null ? "" : extractTicker(instrument));
        return (date == null ? "" : date) + "|" + inst + "|" + accountId + "|" + ownerId;
    }

    private Currency parseCurrency(String code) {
        if (code == null) return Currency.SGD;
        try { return Currency.valueOf(code.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return Currency.SGD; }
    }

    /**
     * Resolve the asset for an imported row, avoiding duplicates. The incoming {@code instrument} may
     * be a bare ticker ("MSFT"), a ticker with an exchange suffix ("MSFT:xnas"), or a descriptive name
     * ("MICROSOFT CORP." / "META PLATFORMS, INC. (META)"). We:
     * <ol>
     *   <li>normalise to a ticker via {@link #extractTicker} and try an exact symbol match;</li>
     *   <li>if the raw value looks like a company name, try to match an existing asset by name
     *       (case/punctuation-insensitive) so "MICROSOFT CORP." reuses your existing asset instead of
     *       creating a duplicate;</li>
     *   <li>only if nothing matches, create a minimal asset.</li>
     * </ol>
     */
    private Asset findOrCreateAsset(String instrument, String currency, Long userId, int[] createdCounter) {
        String raw = instrument.trim();
        String ticker = extractTicker(raw);                 // "NAME (TICKER)" → TICKER; else the value uppercased
        String sym = (ticker == null ? raw : ticker).trim().toUpperCase();

        // 1) Exact symbol match (fast path, matches most imports).
        Optional<Asset> bySymbol = assetService.getBySymbol(sym);
        if (bySymbol.isPresent()) return bySymbol.get();

        // 2) If the incoming value is a company NAME, reuse an existing asset with the same name so a
        //    descriptive statement label doesn't spawn a duplicate.
        if (raw.contains(" ")) {
            String normName = normalizeName(raw);
            Optional<Asset> byName = assetService.getAll().stream()
                    .filter(a -> a.getUserId() == null || a.getUserId().equals(userId))
                    .filter(a -> a.getName() != null && normalizeName(a.getName()).equals(normName))
                    .findFirst();
            if (byName.isPresent()) return byName.get();
        }

        // 3) Create a minimal asset. Use the clean ticker as the symbol; keep the descriptive name.
        Asset a = Asset.builder()
                .userId(userId)
                .name(raw)
                .symbol(sym)
                .assetType(AssetType.OTHER)
                .currency(parseCurrency(currency))
                .build();
        Asset saved = assetService.create(a);
        createdCounter[0]++;
        return saved;
    }

    /** Lower-cased, punctuation-stripped, whitespace-collapsed name for tolerant matching. */
    private String normalizeName(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    // ─────────────────────────── IBKR Flex XML ───────────────────────────

    /**
     * Parse an IBKR Flex "Activity" statement XML into net dividend rows. Flex reports dividends as
     * {@code <CashTransaction>} elements: {@code type="Dividends"} carries the gross amount and
     * {@code type="Withholding Tax"} the (negative) tax. We net the tax into the dividend by
     * matching on symbol + pay date + currency, mirroring the IBKR-CSV logic.
     *
     * <p>Parsed with the JDK StAX reader (no external XML deps). Robust to attribute order and to
     * the {@code <FlexQueryResponse>} vs {@code <FlexStatements>} wrappers Flex uses.
     */
    public List<ParsedDividend> parseFlexXml(String xml) {
        // key = symbol|payDate|currency
        Map<String, BigDecimal> grossByKey = new HashMap<>();
        Map<String, BigDecimal> taxByKey = new HashMap<>();
        Map<String, String[]> metaByKey = new HashMap<>(); // key -> [symbol, date, currency, description]
        // Preserve first-seen order so the output is stable/testable.
        List<String> order = new ArrayList<>();

        try {
            XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(new ByteArrayInputStream(
                    xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            while (r.hasNext()) {
                if (r.next() != XMLStreamReader.START_ELEMENT) continue;
                if (!"CashTransaction".equals(r.getLocalName())) continue;

                String type = attr(r, "type");
                if (type == null) continue;
                String symbol = firstNonBlank(attr(r, "symbol"), attr(r, "underlyingSymbol"));
                String date = firstNonBlank(attr(r, "reportDate"), attr(r, "settleDate"), attr(r, "dateTime"));
                String currency = attr(r, "currency");
                String description = attr(r, "description");
                BigDecimal amount = parseNum(attr(r, "amount"));
                LocalDate payDate = parseFlexDate(date);
                if (symbol == null || payDate == null) continue;
                String key = symbol.trim().toUpperCase() + "|" + payDate + "|" + (currency == null ? "" : currency.trim().toUpperCase());

                String t = type.toLowerCase();
                if (t.contains("withholding")) {
                    taxByKey.merge(key, amount, BigDecimal::add); // amount is negative
                } else if (isDistributionType(t)) {
                    // Sum ALL distribution components for the same symbol+date+currency (ordinary
                    // dividend + return of capital + capital gains, which IBKR reports as separate
                    // cash transactions). This matches the CSV path so the two agree — e.g. a REIT like
                    // ME8U nets to the same total whether fetched live or imported from file.
                    grossByKey.merge(key, amount, BigDecimal::add);
                    if (!metaByKey.containsKey(key)) {
                        metaByKey.put(key, new String[]{symbol.trim().toUpperCase(), payDate.toString(),
                                currency == null ? "USD" : currency.trim().toUpperCase(), description});
                        order.add(key);
                    }
                } else {
                    continue; // interest, fees, deposits, etc. are not dividends
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse IBKR Flex XML: " + e.getMessage(), e);
        }

        List<ParsedDividend> out = new ArrayList<>();
        for (String key : order) {
            BigDecimal gross = grossByKey.getOrDefault(key, BigDecimal.ZERO);
            if (gross.signum() == 0) continue;                       // reversal-netted or tax-only
            BigDecimal tax = taxByKey.getOrDefault(key, BigDecimal.ZERO); // <= 0
            BigDecimal net = gross.add(tax);
            String[] m = metaByKey.get(key);
            out.add(new ParsedDividend(LocalDate.parse(m[1]), m[0], m[2], net,
                    gross, tax.signum() == 0 ? null : tax.abs(), classifyType(m[3])));
        }
        return out;
    }

    /** Persist dividends fetched from an IBKR Flex statement (reuses the shared dedupe/asset logic). */
    @Transactional
    public ImportResult importFlex(String xml, Long userId, Account account, Owner owner) {
        return persist(parseFlexXml(xml), userId, account, owner, "IBKR Flex");
    }

    private String attr(XMLStreamReader r, String name) {
        String v = r.getAttributeValue(null, name);
        return (v == null || v.isBlank()) ? null : v;
    }

    private String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    /** Flex dates are usually yyyyMMdd or yyyy-MM-dd; sometimes with a trailing time. */
    private LocalDate parseFlexDate(String s) {
        if (s == null) return null;
        String d = s.trim();
        int sp = d.indexOf(' ');
        if (sp > 0) d = d.substring(0, sp);
        if (d.contains(";")) d = d.substring(0, d.indexOf(';'));
        try {
            if (d.matches("\\d{8}")) return LocalDate.parse(d, DateTimeFormatter.BASIC_ISO_DATE);
            return LocalDate.parse(d, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) { return null; }
    }

    // ─────────────────────────── IBKR CSV ───────────────────────────

    /**
     * IBKR "Transaction History" CSV. Rows of interest have column[0]="Transaction History",
     * column[1]="Data". Columns: 2=Date, 4=Description, 5=Transaction Type, 6=Symbol, 12=Net Amount.
     * A dividend's net is reduced by the matching "Foreign Tax Withholding" row (same date+symbol).
     */
    List<ParsedDividend> parseIbkr(String csv) {
        // First pass: sum withholding tax per (date, symbol) so we can net it into the dividend.
        Map<String, BigDecimal> taxByKey = new HashMap<>();
        List<String[]> rows = new ArrayList<>();
        for (String line : csv.split("\r?\n")) {
            if (line.isBlank()) continue;
            String[] f = splitCsv(line);
            if (f.length < 13 || !"Transaction History".equals(f[0]) || !"Data".equals(f[1])) continue;
            rows.add(f);
            if ("Foreign Tax Withholding".equals(f[5])) {
                taxByKey.merge(f[2] + "|" + f[6], parseNum(f[12]), BigDecimal::add);
            }
        }

        // Group dividend rows by symbol+date so multiple lines on the same day (e.g. ordinary +
        // return-of-capital + capital-gains components IBKR reports separately) collapse into ONE net
        // dividend — matching the Flex XML path so file import and live fetch agree. Tax for that
        // symbol+date is subtracted once, not once per component row.
        Map<String, BigDecimal> grossByKey = new HashMap<>();
        Map<String, String[]> metaByKey = new HashMap<>(); // key -> [symbol, date, currency, description]
        List<String> order = new ArrayList<>();             // first-seen order for stable output
        for (String[] f : rows) {
            if (!"Dividend".equals(f[5])) continue;
            String date = f[2];
            String symbol = f[6];
            BigDecimal gross = parseNum(f[12]);                                   // positive income
            String taxKey = date + "|" + symbol;
            String currency = taxByKey.containsKey(taxKey) ? "USD" : inferIbkrCurrency(f[4]);
            String key = symbol + "|" + date + "|" + currency;
            grossByKey.merge(key, gross, BigDecimal::add);
            if (!metaByKey.containsKey(key)) {
                metaByKey.put(key, new String[]{symbol, date, currency, f[4]});
                order.add(key);
            }
        }

        List<ParsedDividend> out = new ArrayList<>();
        for (String key : order) {
            String[] m = metaByKey.get(key);
            BigDecimal gross = grossByKey.get(key);
            BigDecimal tax = taxByKey.getOrDefault(m[1] + "|" + m[0], BigDecimal.ZERO); // negative or 0
            BigDecimal net = gross.add(tax);                                      // tax reduces net once
            out.add(new ParsedDividend(LocalDate.parse(m[1]), m[0], m[2],
                    net, gross, tax.signum() == 0 ? null : tax.abs(), classifyType(m[3])));
        }
        return out;
    }

    /** US names carry a "US Tax" row and are USD; SG-listed names settle in SGD. */
    private String inferIbkrCurrency(String description) {
        return description != null && description.contains("USD") ? "USD" : "SGD";
    }

    // ─────────────────────────── Tiger Activity Statement CSV ───────────────────────────

    /**
     * Tiger "Activity Statement" CSV — a multi-section report. Dividend rows live in the section
     * whose first column is "Dividends" with column[3]="DATA". Columns (0-based):
     * 4=Date, 6=Symbol, 9=Phase, 10=Cash Dividends (gross), 12=Fees & Tax (label like
     * "Fee（Include ADR）: 0.71"), 13=Net Cash Value (net), 14=Currency.
     *
     * <p>Only rows in phase "Paid" are imported — "Dividend Accruals Increase" rows are accruals
     * not yet received, so they're skipped (matching the statement's paid vs accrued split).
     */
    List<ParsedDividend> parseTiger(String csv) {
        List<ParsedDividend> out = new ArrayList<>();
        for (String line : splitLinesKeepingQuotedNewlines(csv)) {
            String[] f = splitCsv(line);
            if (f.length < 15 || !"Dividends".equals(f[0]) || !"DATA".equals(f[3])) continue;

            String phase = f[9] == null ? "" : f[9].trim();
            if (!phase.equalsIgnoreCase("Paid")) continue; // skip accruals / non-paid

            LocalDate date = parseIsoDate(f[4]);
            // Tiger's Symbol cell is sometimes the bare ticker ("VOO") and sometimes a descriptive
            // form like "META PLATFORMS, INC. (META)" — extract the ticker in the trailing (...) so
            // it matches the existing asset instead of creating a duplicate.
            String symbol = extractTicker(f[6]);
            BigDecimal gross = parseNum(f[10]);
            BigDecimal net = parseNum(f[13]);
            if (net.signum() == 0 && gross.signum() == 0) continue;
            // "Fees & Tax" is a label like "Fee（Include ADR）: 0.71" — pull the trailing number.
            BigDecimal fee = extractTrailingNumber(f[12]);
            String currency = (f[14] == null || f[14].isBlank()) ? "USD" : f[14].trim().toUpperCase();

            out.add(new ParsedDividend(date, symbol, currency, net,
                    gross.signum() == 0 ? null : gross,
                    (fee == null || fee.signum() == 0) ? null : fee.abs(),
                    "ORDINARY"));
        }
        // Collapse same-day components (e.g. a REIT distribution split across rows) into one dividend,
        // matching the IBKR path so the total is consistent regardless of source.
        return groupSameDay(out);
    }

    /**
     * Extract the ticker from a symbol cell that may be a bare ticker ("VOO") or a descriptive
     * form with the ticker in trailing parentheses ("META PLATFORMS, INC. (META)" → "META").
     */
    private String extractTicker(String cell) {
        if (cell == null) return null;
        String s = cell.trim();
        if (s.isEmpty()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([A-Za-z0-9.\\-]{1,15})\\)\\s*$").matcher(s);
        if (m.find()) return m.group(1).toUpperCase();
        return s.toUpperCase();
    }

    /** Pull the last number out of a label like "Fee（Include ADR）: 0.71" → 0.71. */
    private BigDecimal extractTrailingNumber(String s) {
        if (s == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(-?[0-9]+(?:\\.[0-9]+)?)").matcher(s);
        BigDecimal last = null;
        while (m.find()) last = new BigDecimal(m.group(1));
        return last;
    }

    private LocalDate parseIsoDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE); }
        catch (Exception e) { return null; }
    }

    /**
     * Split into logical CSV lines while keeping quoted fields that contain embedded newlines
     * on one line (Tiger wraps some cells, e.g. trade times, across physical lines).
     */
    private List<String> splitLinesKeepingQuotedNewlines(String csv) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '"') inQuotes = !inQuotes;
            if ((c == '\n' || c == '\r') && !inQuotes) {
                if (cur.length() > 0) { lines.add(cur.toString()); cur.setLength(0); }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    // ─────────────────────────── Saxo XLSX ───────────────────────────

    /**
     * Saxo dividend XLSX export. Columns are resolved by <em>header name</em> (not fixed positions),
     * and the sheet is read reference-aware so blank cells don't shift columns — this makes the import
     * resilient to Saxo re-ordering, renaming or inserting columns between export versions.
     *
     * <p>We recognise: an instrument/symbol column, an event/description column, a pay/value date, the
     * dividend (gross) amount, the withholding tax amount, and the "booked amount" (the net actually
     * credited, usually in the account/base currency). Reversal rows (event contains "Reversal") and
     * the matching original they cancel are both dropped. The booked amount is imported as the net; if
     * no booked column exists we fall back to gross − tax.
     */
    List<ParsedDividend> parseSaxo(byte[] xlsx) {
        List<String> shared = new ArrayList<>();
        List<List<String>> sheet = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            byte[] sharedXml = null, sheetXml = null;
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (name.equals("xl/sharedStrings.xml")) sharedXml = readAll(zis);
                // Accept any worksheet, not just sheet1.xml (Saxo may name it differently).
                else if (sheetXml == null && name.startsWith("xl/worksheets/") && name.endsWith(".xml")) sheetXml = readAll(zis);
            }
            if (sharedXml != null) shared = readSharedStrings(sharedXml);
            if (sheetXml != null) sheet = readSheet(sheetXml, shared);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read Saxo XLSX: " + ex.getMessage(), ex);
        }
        if (sheet.size() < 2) return List.of();

        // Find the header row and map the columns we need by name.
        int headerIdx = -1;
        Map<String, Integer> cols = null;
        for (int i = 0; i < Math.min(sheet.size(), 15); i++) {
            Map<String, Integer> m = mapSaxoDividendColumns(sheet.get(i));
            if (m.containsKey("symbol") && (m.containsKey("booked") || m.containsKey("gross"))) {
                headerIdx = i; cols = m; break;
            }
        }
        if (cols == null) {
            throw new RuntimeException("Could not recognise the Saxo dividends sheet — expected columns like Instrument, Event, Value Date, Dividend, Withholding Tax, Booked Amount");
        }
        final Map<String, Integer> c = cols;
        List<List<String>> data = sheet.subList(headerIdx + 1, sheet.size());

        // Signatures (symbol|payDate|absBooked) that appear as a reversal → drop them and the original.
        java.util.Set<String> reversed = new java.util.HashSet<>();
        for (List<String> r : data) {
            String event = saxoCol(r, c, "event");
            if (event != null && event.toLowerCase().contains("reversal")) reversed.add(reversalSig(r, c));
        }

        List<ParsedDividend> out = new ArrayList<>();
        for (List<String> r : data) {
            String symbol = stripExchange(saxoCol(r, c, "symbol"));
            if (symbol == null || symbol.isBlank()) continue;
            String event = saxoCol(r, c, "event");
            if (event != null && event.toLowerCase().contains("reversal")) continue; // the reversal row
            if (reversed.contains(reversalSig(r, c))) continue;                        // the reversed original

            BigDecimal gross = c.containsKey("gross") ? parseMoneyToken(saxoCol(r, c, "gross")) : BigDecimal.ZERO;
            BigDecimal tax = c.containsKey("tax") ? parseMoneyToken(saxoCol(r, c, "tax")).abs() : BigDecimal.ZERO;
            // Net = the booked amount if present, else gross − tax.
            BigDecimal net = c.containsKey("booked") ? parseNum(saxoCol(r, c, "booked")) : gross.subtract(tax);
            if (net.signum() == 0) continue;

            out.add(new ParsedDividend(excelDate(saxoCol(r, c, "date")), symbol, "SGD", net,
                    gross.signum() == 0 ? null : gross,
                    tax.signum() == 0 ? null : tax,
                    classifyType(event)));
        }
        // Collapse same-day components into one dividend, consistent with the other import paths.
        return groupSameDay(out);
    }

    /**
     * Map Saxo dividend header names → column index. We prefer a real TICKER column ("Instrument
     * Symbol"/"Symbol"/"Ticker"/"UIC") over the descriptive "Instrument" NAME column, and only fall
     * back to the name column when no ticker column exists — otherwise a descriptive name like
     * "MICROSOFT CORP." would be used as the symbol and create duplicate assets.
     */
    private Map<String, Integer> mapSaxoDividendColumns(List<String> header) {
        Map<String, Integer> cols = new HashMap<>();
        Integer nameFallback = null;
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i) == null ? "" : header.get(i).trim().toLowerCase();
            if (h.isEmpty()) continue;
            boolean tickerHeader = h.contains("instrument symbol") || h.equals("symbol")
                    || h.contains("ticker") || h.equals("uic") || h.contains("instrument code");
            if (tickerHeader) { cols.put("symbol", i); }               // ticker column always wins
            else if (nameFallback == null && (h.equals("instrument") || h.contains("instrument name")
                    || h.equals("name") || h.contains("product") || h.contains("security"))) {
                nameFallback = i;                                       // remember, use only if no ticker
            }
            else if (!cols.containsKey("event") && (h.contains("event") || h.contains("description") || h.contains("corporate action"))) cols.put("event", i);
            else if (!cols.containsKey("date") && (h.contains("pay date") || h.contains("value date") || h.contains("payment date") || h.equals("date"))) cols.put("date", i);
            else if (!cols.containsKey("gross") && (h.contains("dividend amount") || h.equals("dividend") || h.contains("gross"))) cols.put("gross", i);
            else if (!cols.containsKey("tax") && (h.contains("withholding") || h.contains("tax"))) cols.put("tax", i);
            else if (!cols.containsKey("booked") && (h.contains("booked amount") || h.contains("booked") || h.contains("net amount") || h.contains("amount booked"))) cols.put("booked", i);
        }
        if (!cols.containsKey("symbol") && nameFallback != null) cols.put("symbol", nameFallback);
        return cols;
    }

    private String saxoCol(List<String> row, Map<String, Integer> cols, String key) {
        Integer i = cols.get(key);
        return i == null ? null : col(row, i);
    }

    private String reversalSig(List<String> r, Map<String, Integer> cols) {
        BigDecimal booked = cols.containsKey("booked") ? parseNum(saxoCol(r, cols, "booked")) : BigDecimal.ZERO;
        return stripExchange(saxoCol(r, cols, "symbol")) + "|" + saxoCol(r, cols, "date") + "|"
                + booked.abs().stripTrailingZeros().toPlainString();
    }

    private String stripExchange(String sym) {
        if (sym == null) return null;
        int i = sym.indexOf(':');
        return (i >= 0 ? sym.substring(0, i) : sym).trim().toUpperCase();
    }

    /**
     * Whether an IBKR Flex CashTransaction {@code type} (lower-cased) is a shareholder distribution we
     * import as a dividend. Covers ordinary dividends, payment-in-lieu, and the REIT/fund components
     * IBKR reports separately — return of capital and capital gains — so the live fetch nets to the
     * same total as the CSV file (which lists all of these as "Dividend" rows).
     */
    private boolean isDistributionType(String t) {
        if (t == null) return false;
        return t.contains("dividend")
                || t.contains("payment in lieu")
                || t.contains("return of capital")
                || t.contains("capital gain")
                || t.contains("distribution");
    }

    /**
     * Collapse same-day distribution components into one dividend per (symbol, pay date, currency).
     * Brokers split a REIT/fund distribution into several rows (ordinary + return of capital + capital
     * gains) on the same date; summing them gives the single net cash actually received and keeps every
     * import path consistent (and consistent with the IBKR live fetch). Net, gross and tax are summed;
     * the first row's type/description wins; first-seen order is preserved.
     */
    private List<ParsedDividend> groupSameDay(List<ParsedDividend> in) {
        Map<String, ParsedDividend> byKey = new java.util.LinkedHashMap<>();
        for (ParsedDividend d : in) {
            String key = (d.symbol() == null ? "" : d.symbol().toUpperCase()) + "|"
                    + (d.payDate() == null ? "" : d.payDate()) + "|"
                    + (d.currency() == null ? "" : d.currency().toUpperCase());
            ParsedDividend prev = byKey.get(key);
            if (prev == null) {
                byKey.put(key, d);
            } else {
                byKey.put(key, new ParsedDividend(
                        prev.payDate(), prev.symbol(), prev.currency(),
                        sum(prev.net(), d.net()),
                        sumNullable(prev.gross(), d.gross()),
                        sumNullable(prev.tax(), d.tax()),
                        prev.type()));
            }
        }
        return new ArrayList<>(byKey.values());
    }

    private BigDecimal sum(BigDecimal a, BigDecimal b) {
        return (a == null ? BigDecimal.ZERO : a).add(b == null ? BigDecimal.ZERO : b);
    }

    /** Sum two possibly-null amounts; returns null only when both are null (so "no value" stays null). */
    private BigDecimal sumNullable(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return null;
        return sum(a, b);
    }

    /** Classify a dividend by its event/description text. */
    private String classifyType(String text) {
        if (text == null) return "ORDINARY";
        String t = text.toLowerCase();
        if (t.contains("return of capital")) return "RETURN_OF_CAPITAL";
        if (t.contains("capital gains")) return "CAPITAL_GAINS";
        if (t.contains("bonus")) return "BONUS";
        if (t.contains("mixed")) return "MIXED";
        return "ORDINARY";
    }

    // ─────────────────────────── low-level helpers ───────────────────────────

    private BigDecimal parseNum(String s) {
        if (s == null) return BigDecimal.ZERO;
        s = s.trim();
        if (s.isEmpty() || s.equals("-")) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    /** Parse a "USD 34.75" / "-USD 8.30" token into a signed number. */
    private BigDecimal parseMoneyToken(String s) {
        if (s == null) return BigDecimal.ZERO;
        var m = java.util.regex.Pattern.compile("(-?)\\s*[A-Z]{3}\\s*(-?[0-9]+(?:\\.[0-9]+)?)").matcher(s.trim());
        if (m.find()) {
            BigDecimal v = new BigDecimal(m.group(2));
            return m.group(1).equals("-") ? v.negate() : v;
        }
        return parseNum(s);
    }

    /** Excel serial date (days since 1899-12-30) or ISO string → LocalDate. */
    private LocalDate excelDate(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        try {
            if (s.matches("\\d+")) return LocalDate.of(1899, 12, 30).plusDays(Long.parseLong(s));
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) { return null; }
    }

    private String col(List<String> row, int i) { return i < row.size() ? row.get(i) : null; }

    private byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private List<String> readSharedStrings(byte[] xml) throws Exception {
        List<String> out = new ArrayList<>();
        XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(new ByteArrayInputStream(xml));
        StringBuilder cur = null;
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamReader.START_ELEMENT && r.getLocalName().equals("si")) cur = new StringBuilder();
            else if (ev == XMLStreamReader.CHARACTERS && cur != null) cur.append(r.getText());
            else if (ev == XMLStreamReader.END_ELEMENT && r.getLocalName().equals("si") && cur != null) {
                out.add(cur.toString());
                cur = null;
            }
        }
        return out;
    }

    /**
     * Read a worksheet into rows of cell strings, resolving each cell's column from its {@code r}
     * reference (e.g. "C7") so blank/omitted cells are preserved as empty strings and columns stay
     * aligned. (An index-only reader collapses gaps and silently shifts every column after a blank.)
     */
    private List<List<String>> readSheet(byte[] xml, List<String> shared) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(new ByteArrayInputStream(xml));
        Map<Integer, String> cellsByCol = null;
        int maxCol = -1;
        String cellType = null, value = null;
        int colIdx = -1;
        boolean inValue = false;
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamReader.START_ELEMENT) {
                switch (r.getLocalName()) {
                    case "row" -> { cellsByCol = new HashMap<>(); maxCol = -1; }
                    case "c" -> {
                        cellType = r.getAttributeValue(null, "t");
                        value = null;
                        colIdx = columnFromRef(r.getAttributeValue(null, "r"));
                    }
                    case "v", "t" -> inValue = true;   // <v> for normal cells, inline <t> for inlineStr
                    default -> { }
                }
            } else if (ev == XMLStreamReader.CHARACTERS && inValue) {
                value = (value == null ? "" : value) + r.getText();
            } else if (ev == XMLStreamReader.END_ELEMENT) {
                switch (r.getLocalName()) {
                    case "v", "t" -> inValue = false;
                    case "c" -> {
                        String resolved = value;
                        if ("s".equals(cellType) && value != null) {
                            int idx = safeInt(value);
                            resolved = (idx >= 0 && idx < shared.size()) ? shared.get(idx) : "";
                        }
                        if (cellsByCol != null && colIdx >= 0) {
                            cellsByCol.put(colIdx, resolved == null ? "" : resolved);
                            if (colIdx > maxCol) maxCol = colIdx;
                        }
                    }
                    case "row" -> {
                        List<String> row = new ArrayList<>();
                        for (int col = 0; col <= maxCol; col++) row.add(cellsByCol == null ? "" : cellsByCol.getOrDefault(col, ""));
                        rows.add(row);
                        cellsByCol = null;
                    }
                    default -> { }
                }
            }
        }
        return rows;
    }

    /** "C7" → 2 (0-based column). Returns -1 if the ref is missing/unparseable. */
    private int columnFromRef(String ref) {
        if (ref == null) return -1;
        int col = 0; boolean any = false;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') { col = col * 26 + (ch - 'A' + 1); any = true; }
            else if (ch >= 'a' && ch <= 'z') { col = col * 26 + (ch - 'a' + 1); any = true; }
            else break;
        }
        return any ? col - 1 : -1;
    }

    private int safeInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
    }

    /** Minimal CSV splitter honouring double-quoted fields (IBKR quotes amounts with commas). */
    private String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else cur.append(c);
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
