package com.myfinance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Draft expense fields extracted from a receipt image via local OCR.
 * This is NOT a persisted entity — it is a suggestion the user reviews and
 * confirms in the UI before an actual {@link com.myfinance.model.Expense} is created.
 * All fields are nullable because OCR extraction is best-effort.
 */
@Data
@Builder
public class ReceiptScanResult {

    /** Parsed transaction date, or null if none could be confidently read. */
    private LocalDate expenseDate;

    /** Suggested description (usually the merchant name from the top of the receipt). */
    private String description;

    /** Parsed total amount (exact, BigDecimal), or null if not found. */
    private BigDecimal amount;

    /** Detected currency code (e.g. "USD"), or null → frontend falls back to the user's base. */
    private String currency;

    /** Suggested category id from the user's own categories, or null if no confident match. */
    private Long suggestedCategoryId;

    /** Human-readable name of the suggested category, for display. */
    private String suggestedCategoryName;

    /** The full raw OCR text, so the user can see what was read and copy details manually. */
    private String rawText;

    /** True when OCR ran but produced no usable text (e.g. blank/unreadable image). */
    private boolean lowConfidence;
}
