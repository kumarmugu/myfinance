package com.myfinance.service;

import com.myfinance.model.*;
import com.myfinance.model.enums.*;
import com.myfinance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionServiceTest {

    @Autowired private TransactionService transactionService;
    @Autowired private HoldingService holdingService;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private AppUser testUser;
    private Asset asset;
    private Account account;
    private Owner owner;

    @BeforeEach
    void setup() {
        holdingRepository.deleteAll();
        transactionRepository.deleteAll();
        assetRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();

        if (appUserRepository.findByUsername("user").isEmpty()) {
            testUser = appUserRepository.save(AppUser.builder()
                    .username("user").email("test@test.com")
                    .password(passwordEncoder.encode("test123"))
                    .displayName("Test User").role("USER").build());
        } else {
            testUser = appUserRepository.findByUsername("user").get();
        }

        owner = ownerRepository.save(Owner.builder()
                .name("Test Owner").relationship(OwnerRelationship.SELF)
                .userId(testUser.getId()).build());
        account = accountRepository.save(Account.builder()
                .name("Tiger Broker").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner)
                .userId(testUser.getId()).build());
        asset = assetRepository.save(Asset.builder()
                .name("VOO").symbol("VOO-TS")
                .assetType(AssetType.INDEX_FUND).currency(Currency.USD)
                .userId(testUser.getId()).build());
    }

    @Test
    @WithMockUser(username = "user")
    void shouldCreateHoldingOnBuy() {
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("400.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 15), "First buy");

        Optional<Holding> holding = holdingService.getHolding(asset.getId(), account.getId(), owner.getId());
        assertTrue(holding.isPresent());
        assertEquals(0, BigDecimal.TEN.compareTo(holding.get().getQuantity()));
        assertEquals(0, new BigDecimal("400.000000").compareTo(holding.get().getAverageBuyPrice()));
        assertEquals(0, new BigDecimal("4000.00").compareTo(holding.get().getInvestedAmount()));
    }

    @Test
    @WithMockUser(username = "user")
    void shouldAveragePriceOnMultipleBuys() {
        // First buy: 10 shares at $400
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("400.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 15), "Buy 1");

        // Second buy: 10 shares at $500
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("500.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 2, 15), "Buy 2");

        Optional<Holding> holding = holdingService.getHolding(asset.getId(), account.getId(), owner.getId());
        assertTrue(holding.isPresent());
        assertEquals(0, new BigDecimal("20").compareTo(holding.get().getQuantity()));

        // Average price: (10*400 + 10*500) / 20 = 9000/20 = 450
        BigDecimal expectedAvg = new BigDecimal("9000.00").divide(new BigDecimal("20"), 6, RoundingMode.HALF_UP);
        assertEquals(0, expectedAvg.compareTo(holding.get().getAverageBuyPrice()));
        assertEquals(0, new BigDecimal("9000.00").compareTo(holding.get().getInvestedAmount()));
    }

    @Test
    @WithMockUser(username = "user")
    void shouldReduceHoldingOnSell() {
        // Buy 20 shares
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, new BigDecimal("20"), new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy");

        // Sell 5 shares
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.SELL, new BigDecimal("5"), new BigDecimal("120.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 3, 1), "Sell");

        Optional<Holding> holding = holdingService.getHolding(asset.getId(), account.getId(), owner.getId());
        assertTrue(holding.isPresent());
        assertEquals(0, new BigDecimal("15").compareTo(holding.get().getQuantity()));
        // Invested = 2000 - (5 * 100 avg) = 1500
        assertEquals(0, new BigDecimal("1500.00").compareTo(holding.get().getInvestedAmount()));
    }

    @Test
    @WithMockUser(username = "user")
    void shouldThrowWhenSellingMoreThanHeld() {
        // Buy 5 shares
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, new BigDecimal("5"), new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy");

        // Try to sell 10 shares
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                transactionService.create(
                        asset.getId(), account.getId(), owner.getId(),
                        TransactionType.SELL, BigDecimal.TEN, new BigDecimal("120.00"),
                        BigDecimal.ZERO, "USD", LocalDate.of(2024, 3, 1), "Oversell"));

        assertEquals("Cannot sell more than held", exception.getMessage());
    }

    @Test
    @WithMockUser(username = "user")
    void shouldThrowWhenSellingWithoutHolding() {
        // Try to sell without any holding
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                transactionService.create(
                        asset.getId(), account.getId(), owner.getId(),
                        TransactionType.SELL, BigDecimal.TEN, new BigDecimal("120.00"),
                        BigDecimal.ZERO, "USD", LocalDate.of(2024, 3, 1), "No holding sell"));

        assertEquals("No holding found to sell", exception.getMessage());
    }

    @Test
    @WithMockUser(username = "user")
    void holdingPreservesInvestmentCurrencyNotBrokerCurrency() {
        // Broker account is USD (see setup); the instrument is an EUR-denominated fund.
        Asset eurFund = assetRepository.save(Asset.builder()
                .name("Euro Stoxx Fund").symbol("ESX-EUR")
                .assetType(AssetType.INDEX_FUND).currency(Currency.EUR)
                .userId(testUser.getId()).build());

        // Buy the EUR fund through the USD broker account. The purchase settles in USD.
        Transaction tx = transactionService.create(
                eurFund.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("50.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 15), "Buy EUR fund via USD broker");

        // Transaction (settlement) currency stays USD — the broker account's currency.
        assertEquals(Currency.USD, tx.getCurrency());

        // Holding (the underlying instrument) must preserve EUR, NOT the broker's USD.
        Optional<Holding> holding = holdingService.getHolding(eurFund.getId(), account.getId(), owner.getId());
        assertTrue(holding.isPresent());
        assertEquals(Currency.EUR, holding.get().getCurrency(),
                "Holding must carry the investment's original currency, not the broker account currency");
    }

    @Test
    @WithMockUser(username = "user")
    void shouldCalculateFeesInTotalAmount() {
        Transaction tx = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("100.00"),
                new BigDecimal("10.00"), "USD", LocalDate.of(2024, 1, 1), "Buy with fees");

        // Total = 10 * 100 + 10 = 1010
        assertEquals(0, new BigDecimal("1010.00").compareTo(tx.getTotalAmount()));
    }
}
