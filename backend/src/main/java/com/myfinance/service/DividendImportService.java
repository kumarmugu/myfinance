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

    public enum Format { IBKR_CSV, SAXO_XLSX }

    @Transactional
    public ImportResult importFile(byte[] content, Format format, Long userId, Account account, Owner owner) {
        List<ParsedDividend> parsed = switch (format) {
            case IBKR_CSV -> parseIbkr(new String(content, java.nio.charset.StandardCharsets.UTF_8));
            case SAXO_XLSX -> parseSaxo(content);
        };

        // De-duplicate against what's already stored, so re-importing the same file is idempotent.
        // Key = date | instrument | accountId | ownerId | amount. We use a COUNT multiset so genuine
        // same-day duplicates in a statement (e.g. two identical return-of-capital rows) still import
        // the correct number of times rather than being over-skipped.
        Map<String, Integer> existing = new HashMap<>();
        for (Dividend d : dividendRepository.findByUserIdOrderByReceivedDateDesc(userId)) {
            existing.merge(dedupeKey(d.getReceivedDate() == null ? null : d.getReceivedDate().toString(),
                    d.getInstrument(), account.getId(), owner.getId(), d.getAmount()), 1, Integer::sum);
        }

        int[] assetsCreated = {0};
        int imported = 0, skipped = 0;
        for (ParsedDividend d : parsed) {
            String key = dedupeKey(d.payDate() == null ? null : d.payDate().toString(),
                    d.symbol(), account.getId(), owner.getId(), d.net());
            Integer already = existing.get(key);
            if (already != null && already > 0) {
                existing.put(key, already - 1); // consume one existing match → skip this row
                skipped++;
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
                    .notes("Imported from " + (format == Format.IBKR_CSV ? "IBKR" : "Saxo") + " statement")
                    .build();
            dividendService.create(div);
            imported++;
        }
        log.info("Dividend import ({}) for userId={}: imported={} skipped={} assetsCreated={}",
                format, userId, imported, skipped, assetsCreated[0]);
        return new ImportResult(imported, skipped, assetsCreated[0]);
    }

    /** Normalized dedupe key. Amount compared at 2dp so trivial scale differences still match. */
    private String dedupeKey(String date, String instrument, Long accountId, Long ownerId, BigDecimal amount) {
        String amt = amount == null ? "" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        String inst = instrument == null ? "" : instrument.trim().toUpperCase();
        return (date == null ? "" : date) + "|" + inst + "|" + accountId + "|" + ownerId + "|" + amt;
    }

    private Currency parseCurrency(String code) {
        if (code == null) return Currency.SGD;
        try { return Currency.valueOf(code.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return Currency.SGD; }
    }

    /** Find an asset by symbol, creating a minimal one (type OTHER) if absent. */
    private Asset findOrCreateAsset(String symbol, String currency, Long userId, int[] createdCounter) {
        String sym = symbol.trim().toUpperCase();
        return assetService.getBySymbol(sym).orElseGet(() -> {
            Asset a = Asset.builder()
                    .userId(userId)
                    .name(sym)
                    .symbol(sym)
                    .assetType(AssetType.OTHER)
                    .currency(parseCurrency(currency))
                    .build();
            Asset saved = assetService.create(a);
            createdCounter[0]++;
            return saved;
        });
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

        List<ParsedDividend> out = new ArrayList<>();
        for (String[] f : rows) {
            if (!"Dividend".equals(f[5])) continue;
            String date = f[2];
            String symbol = f[6];
            BigDecimal gross = parseNum(f[12]);                                   // positive income
            BigDecimal tax = taxByKey.getOrDefault(date + "|" + symbol, BigDecimal.ZERO); // negative or 0
            BigDecimal net = gross.add(tax);                                      // tax reduces net
            String currency = tax.signum() != 0 ? "USD" : inferIbkrCurrency(f[4]);
            out.add(new ParsedDividend(LocalDate.parse(date), symbol, currency,
                    net, gross, tax.signum() == 0 ? null : tax.abs(), classifyType(f[4])));
        }
        return out;
    }

    /** US names carry a "US Tax" row and are USD; SG-listed names settle in SGD. */
    private String inferIbkrCurrency(String description) {
        return description != null && description.contains("USD") ? "USD" : "SGD";
    }

    // ─────────────────────────── Saxo XLSX ───────────────────────────

    /**
     * Saxo "Share Dividends" XLSX. Columns (0-based): 4=Instrument Symbol, 5=Event, 7=Pay Date,
     * 12=Dividend amount ("USD 34.75"), 14=Withholding tax amount, 19=Booked Amount (SGD, net).
     * We import the SGD booked amount as the net. Reversal rows (Event contains "Reversal") and the
     * matching original they cancel are both dropped.
     */
    List<ParsedDividend> parseSaxo(byte[] xlsx) {
        List<String> shared = new ArrayList<>();
        List<List<String>> sheet = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            byte[] sharedXml = null, sheetXml = null;
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().equals("xl/sharedStrings.xml")) sharedXml = readAll(zis);
                else if (e.getName().equals("xl/worksheets/sheet1.xml")) sheetXml = readAll(zis);
            }
            if (sharedXml != null) shared = readSharedStrings(sharedXml);
            if (sheetXml != null) sheet = readSheet(sheetXml, shared);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read Saxo XLSX: " + ex.getMessage(), ex);
        }
        if (sheet.size() < 2) return List.of();

        List<List<String>> data = sheet.subList(1, sheet.size());
        // Signatures (symbol|payDate|absBooked) that appear as a reversal → drop them and the original.
        java.util.Set<String> reversed = new java.util.HashSet<>();
        for (List<String> r : data) {
            String event = col(r, 5);
            if (event != null && event.toLowerCase().contains("reversal")) reversed.add(reversalSig(r));
        }

        List<ParsedDividend> out = new ArrayList<>();
        for (List<String> r : data) {
            String event = col(r, 5);
            if (event == null) continue;
            if (event.toLowerCase().contains("reversal")) continue;   // the reversal row
            if (reversed.contains(reversalSig(r))) continue;          // the reversed original
            BigDecimal bookedSgd = parseNum(col(r, 19));
            if (bookedSgd.signum() == 0) continue;
            BigDecimal grossUsd = parseMoneyToken(col(r, 12));
            BigDecimal taxUsd = parseMoneyToken(col(r, 14)).abs();
            out.add(new ParsedDividend(excelDate(col(r, 7)), stripExchange(col(r, 4)), "SGD", bookedSgd,
                    grossUsd.signum() == 0 ? null : grossUsd,
                    taxUsd.signum() == 0 ? null : taxUsd,
                    classifyType(event)));
        }
        return out;
    }

    private String reversalSig(List<String> r) {
        return stripExchange(col(r, 4)) + "|" + col(r, 7) + "|"
                + parseNum(col(r, 19)).abs().stripTrailingZeros().toPlainString();
    }

    private String stripExchange(String sym) {
        if (sym == null) return null;
        int i = sym.indexOf(':');
        return (i >= 0 ? sym.substring(0, i) : sym).trim().toUpperCase();
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

    private List<List<String>> readSheet(byte[] xml, List<String> shared) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(new ByteArrayInputStream(xml));
        List<String> row = null;
        String cellType = null, value = null;
        boolean inValue = false;
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamReader.START_ELEMENT) {
                switch (r.getLocalName()) {
                    case "row" -> row = new ArrayList<>();
                    case "c" -> { cellType = r.getAttributeValue(null, "t"); value = null; }
                    case "v" -> inValue = true;
                    default -> { }
                }
            } else if (ev == XMLStreamReader.CHARACTERS && inValue) {
                value = (value == null ? "" : value) + r.getText();
            } else if (ev == XMLStreamReader.END_ELEMENT) {
                switch (r.getLocalName()) {
                    case "v" -> inValue = false;
                    case "c" -> {
                        String resolved = value;
                        if ("s".equals(cellType) && value != null) {
                            int idx = Integer.parseInt(value);
                            resolved = idx < shared.size() ? shared.get(idx) : "";
                        }
                        if (row != null) row.add(resolved == null ? "" : resolved);
                    }
                    case "row" -> { if (row != null) rows.add(row); row = null; }
                    default -> { }
                }
            }
        }
        return rows;
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
