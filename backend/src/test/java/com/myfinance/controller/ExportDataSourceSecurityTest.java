package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.*;
import com.myfinance.model.enums.*;
import com.myfinance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Table export is generated entirely on the frontend from the data the list endpoints
 * return (there is no server-side pagination — the endpoints already return the complete
 * per-user dataset). Therefore the security guarantee for exports IS the security
 * guarantee of those data-source endpoints:
 *   - authentication is required (unauthenticated -> 401/403, never data),
 *   - the response is tenant-isolated (only the caller's records),
 *   - the response is complete (every one of the caller's records, not a page).
 *
 * This class pins those guarantees for the endpoints that back the exportable tables
 * (transactions, accounts, dividends, holdings). If any of these ever started leaking
 * another tenant's rows, the corresponding export would leak too — so these tests are
 * the export feature's security regression guard.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExportDataSourceSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private DividendRepository dividendRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private AppUser userA;
    private AppUser userB;

    @BeforeEach
    void setup() {
        dividendRepository.deleteAll();
        holdingRepository.deleteAll();
        transactionRepository.deleteAll();
        assetRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();

        userA = ensureUser("exportUserA", "ea@test.com");
        userB = ensureUser("exportUserB", "eb@test.com");

        seedFor(userA, "A");
        seedFor(userB, "B");
    }

    private AppUser ensureUser(String username, String email) {
        return appUserRepository.findByUsername(username).orElseGet(() ->
                appUserRepository.save(AppUser.builder()
                        .username(username).email(email)
                        .password(passwordEncoder.encode("pass"))
                        .displayName(username).role("USER").build()));
    }

    /** Seeds one owner, one account, one asset, two transactions, one dividend for a user. */
    private void seedFor(AppUser user, String tag) {
        Owner owner = ownerRepository.save(Owner.builder()
                .name(tag + "-Owner").relationship(OwnerRelationship.SELF).userId(user.getId()).build());
        Account account = accountRepository.save(Account.builder()
                .name(tag + "-Broker").accountType(AccountType.BROKER).currency(Currency.USD)
                .owner(owner).userId(user.getId()).build());
        Asset asset = assetRepository.save(Asset.builder()
                .name(tag + "-Fund").symbol(tag + "-SYM").assetType(AssetType.INDEX_FUND)
                .currency(Currency.USD).userId(user.getId()).build());

        for (int i = 0; i < 2; i++) {
            transactionRepository.save(Transaction.builder()
                    .asset(asset).account(account).owner(owner)
                    .transactionType(TransactionType.BUY)
                    .quantity(new BigDecimal("10")).pricePerUnit(new BigDecimal("100"))
                    .totalAmount(new BigDecimal("1000")).fees(BigDecimal.ZERO)
                    .currency(Currency.USD).transactionDate(LocalDate.of(2026, 1, 1 + i))
                    .userId(user.getId()).build());
        }
        dividendRepository.save(Dividend.builder()
                .account(account).owner(owner).amount(new BigDecimal("42")).currency(Currency.USD)
                .receivedDate(LocalDate.of(2026, 3, 1)).year(2026).quarter("Q1").instrument(tag + "-INSTR")
                .userId(user.getId()).build());
        holdingRepository.save(Holding.builder()
                .asset(asset).account(account).owner(owner)
                .quantity(new BigDecimal("20")).averageBuyPrice(new BigDecimal("100"))
                .investedAmount(new BigDecimal("2000")).currency(Currency.USD)
                .userId(user.getId()).build());
    }

    // ─── Authentication ───

    @Test
    void unauthenticatedCannotReadTransactions() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().is4xxClientError()); // 401/403, never data
    }

    @Test
    void unauthenticatedCannotReadAccounts() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().is4xxClientError());
    }

    @Test
    void unauthenticatedCannotReadDividends() throws Exception {
        mockMvc.perform(get("/api/dividends")).andExpect(status().is4xxClientError());
    }

    @Test
    void unauthenticatedCannotReadHoldings() throws Exception {
        mockMvc.perform(get("/api/holdings")).andExpect(status().is4xxClientError());
    }

    // ─── Tenant isolation + completeness (the exportable dataset) ───

    @Test
    @WithMockUser(username = "exportUserA")
    void transactionsExportDatasetIsCompleteAndTenantIsolated() throws Exception {
        // Exactly A's two transactions — never B's, and not a truncated page.
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].asset.symbol", everyItem(is("A-SYM"))));
    }

    @Test
    @WithMockUser(username = "exportUserB")
    void userBSeesOnlyOwnTransactions() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].asset.symbol", everyItem(is("B-SYM"))));
    }

    @Test
    @WithMockUser(username = "exportUserA")
    void accountsExportDatasetIsTenantIsolated() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("A-Broker")));
    }

    @Test
    @WithMockUser(username = "exportUserA")
    void dividendsExportDatasetIsTenantIsolated() throws Exception {
        mockMvc.perform(get("/api/dividends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].instrument", is("A-INSTR")));
    }

    @Test
    @WithMockUser(username = "exportUserA")
    void holdingsExportDatasetIsTenantIsolated() throws Exception {
        // Both users hold positions; A must see only its own.
        mockMvc.perform(get("/api/holdings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].asset.symbol", is("A-SYM")));
    }
}
