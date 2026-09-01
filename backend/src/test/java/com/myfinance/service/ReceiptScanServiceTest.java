package com.myfinance.service;

import com.myfinance.model.BudgetCategory;
import com.myfinance.repository.BudgetCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the pure parsing heuristics in {@link ReceiptScanService}.
 * OCR itself needs the native Tesseract library, so we exercise the package-private
 * parse methods directly with representative receipt text instead.
 */
@ExtendWith(MockitoExtension.class)
class ReceiptScanServiceTest {

    @Mock private BudgetCategoryRepository categoryRepository;
    @InjectMocks private ReceiptScanService service;

    private BudgetCategory cat(long id, String name) {
        return BudgetCategory.builder().id(id).name(name).userId(1L).isActive(true).build();
    }

    // ─── Amount ───

    @Test
    void picksTotalLineOverOtherAmounts() {
        String text = "MegaMart\nMilk 3.50\nBread 2.00\nSubtotal 5.50\nTotal 5.90";
        assertThat(service.parseAmount(text)).isEqualByComparingTo("5.90");
    }

    @Test
    void ignoresSubtotalWhenTotalPresent() {
        String text = "Sub Total 100.00\nGST 7.00\nGrand Total 107.00";
        assertThat(service.parseAmount(text)).isEqualByComparingTo("107.00");
    }

    @Test
    void fallsBackToLargestAmountWhenNoTotalKeyword() {
        String text = "Item A 12.00\nItem B 45.90\nItem C 3.00";
        assertThat(service.parseAmount(text)).isEqualByComparingTo("45.90");
    }

    @Test
    void handlesThousandsSeparators() {
        String text = "TOTAL 1,234.56";
        assertThat(service.parseAmount(text)).isEqualByComparingTo("1234.56");
    }

    @Test
    void returnsNullWhenNoAmount() {
        assertThat(service.parseAmount("no numbers here")).isNull();
        assertThat(service.parseAmount("")).isNull();
    }

    // ─── Date ───

    @Test
    void parsesSlashDate() {
        assertThat(service.parseDate("Date: 15/03/2024")).isEqualTo(LocalDate.of(2024, 3, 15));
    }

    @Test
    void parsesIsoDate() {
        assertThat(service.parseDate("2023-11-02 receipt")).isEqualTo(LocalDate.of(2023, 11, 2));
    }

    @Test
    void ignoresFutureDates() {
        // A far-future date should not be accepted as the transaction date.
        LocalDate future = LocalDate.now().plusYears(5);
        String token = future.getDayOfMonth() + "/" + future.getMonthValue() + "/" + future.getYear();
        assertThat(service.parseDate("Total 9.99 " + token)).isNull();
    }

    @Test
    void returnsNullWhenNoDate() {
        assertThat(service.parseDate("just some text")).isNull();
    }

    // ─── Currency ───

    @Test
    void detectsCurrencyCode() {
        assertThat(service.parseCurrency("Total 20.00 USD")).isEqualTo("USD");
    }

    @Test
    void detectsCurrencySymbol() {
        assertThat(service.parseCurrency("Total \u20ac15,00")).isEqualTo("EUR");
    }

    @Test
    void returnsNullForUnknownCurrency() {
        assertThat(service.parseCurrency("Total 100")).isNull();
    }

    // ─── Merchant ───

    @Test
    void usesFirstAlphabeticLineAsMerchant() {
        String text = "FreshMart Groceries\n123 Main St\nTotal 9.90";
        assertThat(service.parseMerchant(text)).isEqualTo("FreshMart Groceries");
    }

    @Test
    void skipsNumericHeaderLines() {
        String text = "12345\nCorner Cafe\nTotal 4.50";
        assertThat(service.parseMerchant(text)).isEqualTo("Corner Cafe");
    }

    // ─── Category suggestion ───

    @Test
    void directCategoryNameMatchWins() {
        when(categoryRepository.findByUserIdAndIsActiveTrueOrderBySortOrderAsc(1L))
                .thenReturn(List.of(cat(1, "Groceries"), cat(2, "Transport")));
        BudgetCategory result = service.suggestCategory("Weekly groceries at the store", 1L);
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Groceries");
    }

    @Test
    void keywordMatchMapsToCategory() {
        when(categoryRepository.findByUserIdAndIsActiveTrueOrderBySortOrderAsc(1L))
                .thenReturn(List.of(cat(1, "Dining"), cat(2, "Transport")));
        // "restaurant" is a dining keyword; the "Dining" category name contains "dining".
        BudgetCategory result = service.suggestCategory("Dinner at Luigi Restaurant", 1L);
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Dining");
    }

    @Test
    void returnsNullWhenNoCategoriesExist() {
        when(categoryRepository.findByUserIdAndIsActiveTrueOrderBySortOrderAsc(1L))
                .thenReturn(List.of());
        assertThat(service.suggestCategory("anything", 1L)).isNull();
    }

    @Test
    void returnsNullWhenNothingMatches() {
        when(categoryRepository.findByUserIdAndIsActiveTrueOrderBySortOrderAsc(1L))
                .thenReturn(List.of(cat(1, "Housing")));
        assertThat(service.suggestCategory("random gibberish text", 1L)).isNull();
    }

    // ─── Availability ───

    @Test
    void notAvailableWhenTessdataPathUnset() {
        // Fresh service with no @Value injection has a blank tessdata path.
        ReceiptScanService fresh = new ReceiptScanService(categoryRepository);
        assertThat(fresh.isAvailable()).isFalse();
    }

    @Test
    void scanThrowsWhenUnavailable() {
        ReceiptScanService fresh = new ReceiptScanService(categoryRepository);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> fresh.scan(null, 1L));
    }
}
