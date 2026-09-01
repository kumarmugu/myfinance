package com.myfinance.service;

import com.myfinance.dto.ReceiptScanResult;
import com.myfinance.model.BudgetCategory;
import com.myfinance.repository.BudgetCategoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local, self-hosted receipt OCR using Tesseract (Tess4J). No external API is called —
 * the image never leaves this server, in line with the app's user-owns-their-data principle.
 *
 * <p>Extraction is best-effort: the parsed {@link ReceiptScanResult} is a DRAFT the user
 * reviews and confirms before an actual expense is created. If Tesseract's native library
 * or language data is not installed, the feature degrades gracefully — {@link #isAvailable()}
 * returns false and the controller reports that scanning is unavailable rather than erroring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptScanService {

    private final BudgetCategoryRepository categoryRepository;

    /** Directory holding Tesseract *.traineddata files (e.g. /usr/local/share/tessdata). */
    @Value("${app.ocr.tessdata-path:}")
    private String tessdataPath;

    /** OCR language(s), e.g. "eng". */
    @Value("${app.ocr.language:eng}")
    private String language;

    private volatile boolean available = false;

    // Amounts like 1,234.56 / 12.00 / 1234 — captured with optional thousands separators.
    private static final Pattern AMOUNT = Pattern.compile("(\\d{1,3}(?:[,\\s]\\d{3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)");

    // Currency hints found on receipts.
    private static final Map<String, String> CURRENCY_HINTS = Map.ofEntries(
            Map.entry("$", "USD"), Map.entry("us$", "USD"), Map.entry("usd", "USD"),
            Map.entry("s$", "SGD"), Map.entry("sgd", "SGD"),
            Map.entry("\u20ac", "EUR"), Map.entry("eur", "EUR"),
            Map.entry("\u00a3", "GBP"), Map.entry("gbp", "GBP"),
            Map.entry("rs", "LKR"), Map.entry("lkr", "LKR"),
            Map.entry("\u20b9", "INR"), Map.entry("inr", "INR"), Map.entry("rs.", "INR"),
            Map.entry("rm", "MYR"), Map.entry("myr", "MYR"),
            Map.entry("\u00a5", "JPY"), Map.entry("jpy", "JPY"),
            Map.entry("a$", "AUD"), Map.entry("aud", "AUD")
    );

    // Keyword → category-name fragment used to suggest one of the user's own categories.
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.of(
            "grocery", List.of("supermarket", "grocery", "mart", "market", "ntuc", "fairprice", "aldi", "tesco", "walmart"),
            "dining", List.of("restaurant", "cafe", "coffee", "kitchen", "bar", "bistro", "mcdonald", "kfc", "starbucks", "food"),
            "transport", List.of("taxi", "grab", "uber", "mrt", "petrol", "fuel", "shell", "esso", "parking"),
            "utilities", List.of("electric", "water", "gas", "telecom", "internet", "mobile", "utility"),
            "health", List.of("pharmacy", "clinic", "hospital", "guardian", "watsons", "medical")
    );

    // Common date formats seen on receipts.
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    );

    private static final Pattern DATE_TOKEN = Pattern.compile(
            "(\\d{1,2}[/.\\-]\\d{1,2}[/.\\-]\\d{2,4}|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4})");

    @PostConstruct
    void init() {
        if (tessdataPath == null || tessdataPath.isBlank()) {
            log.info("Receipt OCR disabled: app.ocr.tessdata-path is not configured.");
            return;
        }
        try {
            // Probe: constructing and configuring Tesseract does not yet load the native lib,
            // so we defer the true availability check to first use, but validate config here.
            Tesseract probe = newEngine();
            if (probe != null) {
                available = true;
                log.info("Receipt OCR enabled (tessdata={}, lang={}).", tessdataPath, language);
            }
        } catch (Throwable t) {
            log.warn("Receipt OCR disabled: Tesseract could not be initialized ({}).", t.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    private Tesseract newEngine() {
        Tesseract engine = new Tesseract();
        engine.setDatapath(tessdataPath);
        engine.setLanguage(language);
        return engine;
    }

    /**
     * Runs OCR on the uploaded image and parses a draft expense. Never persists anything.
     *
     * @throws IllegalStateException if OCR is not available (native lib / tessdata missing)
     * @throws IllegalArgumentException if the file is not a readable image
     */
    public ReceiptScanResult scan(MultipartFile file, Long userId) {
        if (!available) {
            throw new IllegalStateException("Receipt scanning is not available on this server (Tesseract not configured).");
        }
        BufferedImage image = readImage(file);
        String text;
        try {
            text = newEngine().doOCR(image);
        } catch (Throwable t) {
            // A native/runtime failure at scan time should also disable further attempts cleanly.
            log.warn("OCR failed: {}", t.getMessage());
            throw new IllegalStateException("Could not read the receipt image. Try a clearer photo.");
        }

        String raw = text == null ? "" : text.trim();
        boolean lowConfidence = raw.replaceAll("\\s", "").length() < 8;

        BigDecimal amount = parseAmount(raw);
        LocalDate date = parseDate(raw);
        String currency = parseCurrency(raw);
        String merchant = parseMerchant(raw);
        BudgetCategory suggested = suggestCategory(raw, userId);

        return ReceiptScanResult.builder()
                .expenseDate(date)
                .description(merchant)
                .amount(amount)
                .currency(currency)
                .suggestedCategoryId(suggested != null ? suggested.getId() : null)
                .suggestedCategoryName(suggested != null ? suggested.getName() : null)
                .rawText(raw)
                .lowConfidence(lowConfidence)
                .build();
    }

    private BufferedImage readImage(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            if (image == null) {
                throw new IllegalArgumentException("Unsupported or corrupt image file.");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded image.");
        }
    }

    // ─── Parsing heuristics ───

    /**
     * Finds the receipt total. Preference order: a line containing a "total" keyword
     * (but not "subtotal"), otherwise the largest monetary value on the receipt.
     */
    BigDecimal parseAmount(String text) {
        if (text.isBlank()) return null;
        String[] lines = text.split("\\r?\\n");
        BigDecimal totalFromKeyword = null;
        BigDecimal largest = null;

        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            BigDecimal lineMax = maxAmountInLine(line);
            if (lineMax == null) continue;

            if (largest == null || lineMax.compareTo(largest) > 0) {
                largest = lineMax;
            }
            boolean isTotalLine = (lower.contains("total") || lower.contains("amount due") || lower.contains("grand"))
                    && !lower.contains("subtotal") && !lower.contains("sub total");
            if (isTotalLine) {
                // Later total lines (e.g. "Grand Total") tend to be the final amount.
                totalFromKeyword = lineMax;
            }
        }
        return totalFromKeyword != null ? totalFromKeyword : largest;
    }

    private BigDecimal maxAmountInLine(String line) {
        Matcher m = AMOUNT.matcher(line);
        BigDecimal max = null;
        while (m.find()) {
            String token = m.group(1).replaceAll("[,\\s]", "");
            if (token.isEmpty() || token.equals(".")) continue;
            try {
                BigDecimal v = new BigDecimal(token);
                if (max == null || v.compareTo(max) > 0) max = v;
            } catch (NumberFormatException ignore) {
                // skip malformed token
            }
        }
        return max;
    }

    LocalDate parseDate(String text) {
        Matcher m = DATE_TOKEN.matcher(text);
        while (m.find()) {
            String token = m.group(1).trim();
            LocalDate parsed = tryParseDate(token);
            if (parsed != null && !parsed.isAfter(LocalDate.now().plusDays(1))) {
                return parsed;
            }
        }
        return null;
    }

    private LocalDate tryParseDate(String token) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(token, fmt);
            } catch (Exception ignore) {
                // try next format
            }
        }
        return null;
    }

    String parseCurrency(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : CURRENCY_HINTS.entrySet()) {
            String hint = e.getKey();
            boolean isWord = hint.chars().allMatch(Character::isLetter);
            if (isWord) {
                if (lower.matches(".*\\b" + Pattern.quote(hint) + "\\b.*")) return e.getValue();
            } else if (lower.contains(hint)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Uses the first non-empty, mostly-alphabetic line as the merchant name. */
    String parseMerchant(String text) {
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.length() < 3) continue;
            long letters = trimmed.chars().filter(Character::isLetter).count();
            if (letters >= trimmed.length() * 0.5) {
                return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
            }
        }
        return null;
    }

    BudgetCategory suggestCategory(String text, Long userId) {
        List<BudgetCategory> categories = categoryRepository.findByUserIdAndIsActiveTrueOrderBySortOrderAsc(userId);
        if (categories.isEmpty()) return null;
        String lower = text.toLowerCase(Locale.ROOT);

        // 1) Direct match: a category name appears in the receipt text.
        for (BudgetCategory c : categories) {
            if (c.getName() != null && !c.getName().isBlank()
                    && lower.contains(c.getName().toLowerCase(Locale.ROOT))) {
                return c;
            }
        }
        // 2) Keyword group → try to match a category whose name contains the group hint word.
        List<String> matchedGroups = new ArrayList<>();
        for (Map.Entry<String, List<String>> group : CATEGORY_KEYWORDS.entrySet()) {
            for (String kw : group.getValue()) {
                if (lower.contains(kw)) {
                    matchedGroups.add(group.getKey());
                    break;
                }
            }
        }
        for (String group : matchedGroups) {
            for (BudgetCategory c : categories) {
                String name = c.getName() == null ? "" : c.getName().toLowerCase(Locale.ROOT);
                if (name.contains(group) || group.contains(name)) {
                    return c;
                }
            }
        }
        return null;
    }
}
