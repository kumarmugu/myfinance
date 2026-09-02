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
    @Autowired private SoldPositionRepository soldPositionRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private AppUser testUser;
    private Asset asset;
    private Account account;
    private Owner owner;

    @BeforeEach
    void setup() {
        soldPositionRepository.deleteAll();
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

    @Test
    @WithMockUser(username = "user")
    void storesFeeCurrencyAndPurchaseFxRateForCrossCurrencyBuy() {
        // USD-priced asset bought via an SGD account: capture SGD fee currency + purchase FX rate.
        Transaction tx = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("100.00"),
                new BigDecimal("12.00"), "USD", LocalDate.of(2024, 1, 1), "Saxo buy", null,
                "SGD", new BigDecimal("1.35"));

        assertEquals("SGD", tx.getFeeCurrency());
        assertEquals(0, new BigDecimal("1.35").compareTo(tx.getFxRateToBase()));
    }

    @Test
    @WithMockUser(username = "user")
    void defaultsFeeCurrencyAndFxRateToNullForPlainBuy() {
        Transaction tx = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Plain buy");
        assertNull(tx.getFeeCurrency());
        assertNull(tx.getFxRateToBase());
    }

    @Test
    @WithMockUser(username = "user")
    void realizedPnlSplitsStockAndFxForCrossCurrencySell() {
        // Buy 10 @ $100 with USD→SGD = 1.30 (cost basis 1300 SGD/lot rate).
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy", null,
                "SGD", new BigDecimal("1.30"));

        // Sell 10 @ $120 with USD→SGD = 1.40, no fee.
        Transaction sell = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.SELL, BigDecimal.TEN, new BigDecimal("120.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 6, 1), "Sell", null,
                null, new BigDecimal("1.40"));

        // Stock component: (10*120 - 10*100) * buyFx(1.30) = 200 * 1.30 = 260
        assertEquals(0, new BigDecimal("260.00").compareTo(sell.getRealizedStockPnl().setScale(2, RoundingMode.HALF_UP)));
        // FX component: proceeds(10*120=1200) * (sellFx 1.40 - buyFx 1.30) = 1200 * 0.10 = 120
        assertEquals(0, new BigDecimal("120.00").compareTo(sell.getRealizedFxPnl().setScale(2, RoundingMode.HALF_UP)));
        // Total = 260 + 120 = 380
        assertEquals(0, new BigDecimal("380.00").compareTo(sell.getRealizedPnl().setScale(2, RoundingMode.HALF_UP)));
    }

    @Test
    @WithMockUser(username = "user")
    void realizedPnlSubtractsSellFee() {
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy", null,
                "SGD", new BigDecimal("1.30"));

        // Sell 10 @ $120, sellFx 1.40. The test account currency is USD, so a fee in the account
        // currency (USD) is subtracted as-is (no conversion): 15 USD fee.
        Transaction sell = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.SELL, BigDecimal.TEN, new BigDecimal("120.00"),
                new BigDecimal("15.00"), "USD", LocalDate.of(2024, 6, 1), "Sell", null,
                "USD", new BigDecimal("1.40"));

        // Total from the split test (380) minus the 15 fee (account currency) = 365.
        assertEquals(0, new BigDecimal("365.00").compareTo(sell.getRealizedPnl().setScale(2, RoundingMode.HALF_UP)));
    }

    @Test
    @WithMockUser(username = "user")
    void buyWithoutFxRateDoesNotInventFxGainOnSell() {
        // Regression: the BUY was entered WITHOUT an fx rate (older data). If we default the buy FX
        // to 1 while the SELL carries a real rate, we manufacture a phantom FX gain of
        // proceeds×(sellFx−1). Instead the FX component must be zero and P/L = (proceeds−cost)×sellFx.
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy without fx");

        Transaction sell = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.SELL, BigDecimal.TEN, new BigDecimal("120.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 6, 1), "Sell with fx", null,
                null, new BigDecimal("1.28"));

        // No phantom FX gain.
        assertEquals(0, BigDecimal.ZERO.compareTo(sell.getRealizedFxPnl().setScale(2, RoundingMode.HALF_UP)));
        // Whole P/L = (120-100)*10 * 1.28 = 200 * 1.28 = 256.
        assertEquals(0, new BigDecimal("256.00").compareTo(sell.getRealizedPnl().setScale(2, RoundingMode.HALF_UP)));
    }

    @Test
    @WithMockUser(username = "user")
    void realizedPnlSameCurrencyHasNoFxComponent() {
        // Plain USD buy/sell (no fx rates) → FX component is zero, stock component is the whole P/L.
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy");
        Transaction sell = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.SELL, BigDecimal.TEN, new BigDecimal("130.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 6, 1), "Sell");

        assertEquals(0, BigDecimal.ZERO.compareTo(sell.getRealizedFxPnl().setScale(2, RoundingMode.HALF_UP)));
        // (130-100)*10 = 300
        assertEquals(0, new BigDecimal("300.00").compareTo(sell.getRealizedPnl().setScale(2, RoundingMode.HALF_UP)));
    }

    @Test
    @WithMockUser(username = "user")
    void buyLeavesRealizedPnlNull() {
        Transaction buy = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy");
        assertNull(buy.getRealizedPnl());
    }

    @Test
    @WithMockUser(username = "user")
    void updateBuyRecalculatesHolding() {
        Transaction tx = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy 10 @100");

        // Correct the buy: 12 shares at $90 (e.g. fixing a typo).
        transactionService.update(
                tx.getId(), asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, new BigDecimal("12"), new BigDecimal("90.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Corrected", null);

        Optional<Holding> h = holdingService.getHolding(asset.getId(), account.getId(), owner.getId());
        assertTrue(h.isPresent());
        // Old effect (10@100) fully reversed, new effect (12@90) applied.
        assertEquals(0, new BigDecimal("12").compareTo(h.get().getQuantity()));
        assertEquals(0, new BigDecimal("1080.00").compareTo(h.get().getInvestedAmount()));
        assertEquals(0, new BigDecimal("90.000000").compareTo(h.get().getAverageBuyPrice()));
    }

    @Test
    @WithMockUser(username = "user")
    void updateBuyForStockSplitDoublesQuantityHalvesPrice() {
        // Original lot: 10 shares at $200 (invested 2000).
        Transaction tx = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("200.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Pre-split");

        // 2-for-1 split: 20 shares at $100 — invested amount unchanged at 2000.
        transactionService.update(
                tx.getId(), asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, new BigDecimal("20"), new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Post 2:1 split", null);

        Optional<Holding> h = holdingService.getHolding(asset.getId(), account.getId(), owner.getId());
        assertTrue(h.isPresent());
        assertEquals(0, new BigDecimal("20").compareTo(h.get().getQuantity()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(h.get().getInvestedAmount()));
        assertEquals(0, new BigDecimal("100.000000").compareTo(h.get().getAverageBuyPrice()));
    }

    @Test
    @WithMockUser(username = "user")
    void updateOnlyAffectsTheEditedLotWhenMultipleBuysExist() {
        // Two separate buys of the same asset in the same holding.
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy A: 10@100");
        Transaction b = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("200.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 2, 1), "Buy B: 10@200");
        // Holding now: 20 shares, invested 3000, avg 150.

        // Edit only buy B to 10@300.
        transactionService.update(
                b.getId(), asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("300.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 2, 1), "Buy B corrected", null);

        Optional<Holding> h = holdingService.getHolding(asset.getId(), account.getId(), owner.getId());
        assertTrue(h.isPresent());
        // Reversed B(10@200) then applied B(10@300): 20 shares, invested 1000+3000=4000, avg 200.
        assertEquals(0, new BigDecimal("20").compareTo(h.get().getQuantity()));
        assertEquals(0, new BigDecimal("4000.00").compareTo(h.get().getInvestedAmount()));
        assertEquals(0, new BigDecimal("200.000000").compareTo(h.get().getAverageBuyPrice()));
    }

    // ── Portfolio → Sold tab sync (a SELL transaction generates a SoldPosition) ──

    @Test
    @WithMockUser(username = "user")
    void sellCreatesSoldPositionWithRealizedProfit() {
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy");
        Transaction sell = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.SELL, BigDecimal.TEN, new BigDecimal("130.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 6, 1), "Sell");

        Optional<SoldPosition> spOpt = soldPositionRepository.findBySourceTransactionId(sell.getId());
        assertTrue(spOpt.isPresent(), "A SELL must generate a matching sold position");
        SoldPosition sp = spOpt.get();

        // Profit reuses the transaction's realized P/L (same-currency: (130-100)*10 = 300).
        assertEquals(0, sell.getRealizedPnl().compareTo(sp.getProfit()));
        assertEquals(0, new BigDecimal("300.00").compareTo(sp.getProfit().setScale(2, RoundingMode.HALF_UP)));
        assertEquals(0, BigDecimal.TEN.compareTo(sp.getQuantity()));
        assertEquals(0, new BigDecimal("100").compareTo(sp.getBuyPrice().setScale(0, RoundingMode.HALF_UP)));
        assertEquals(0, new BigDecimal("130.00").compareTo(sp.getSellPrice()));
        assertEquals(LocalDate.of(2024, 6, 1), sp.getSoldDate());
        // investedDate = earliest BUY date.
        assertEquals(LocalDate.of(2024, 1, 1), sp.getInvestedDate());
        assertEquals(Currency.USD, sp.getCurrency());
        assertEquals(sell.getId(), sp.getSourceTransactionId());
        assertEquals(testUser.getId(), sp.getUserId());
    }

    @Test
    @WithMockUser(username = "user")
    void buyDoesNotCreateSoldPosition() {
        Transaction buy = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy");
        assertTrue(soldPositionRepository.findBySourceTransactionId(buy.getId()).isEmpty());
        assertEquals(0, soldPositionRepository.count());
    }

    @Test
    @WithMockUser(username = "user")
    void editingSellUpdatesTheSameSoldPositionInPlace() {
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, new BigDecimal("20"), new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy");
        Transaction sell = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.SELL, BigDecimal.TEN, new BigDecimal("130.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 6, 1), "Sell");

        Long spId = soldPositionRepository.findBySourceTransactionId(sell.getId()).orElseThrow().getId();

        // Correct the sell price to $150.
        Transaction updated = transactionService.update(
                sell.getId(), asset.getId(), account.getId(), owner.getId(),
                TransactionType.SELL, BigDecimal.TEN, new BigDecimal("150.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 6, 1), "Sell corrected", null);

        // Still exactly one sold position, same row (updated in place), reflecting the new profit.
        assertEquals(1, soldPositionRepository.count());
        SoldPosition sp = soldPositionRepository.findBySourceTransactionId(sell.getId()).orElseThrow();
        assertEquals(spId, sp.getId(), "The existing sold position must be updated, not duplicated");
        assertEquals(0, new BigDecimal("150.00").compareTo(sp.getSellPrice()));
        assertEquals(0, updated.getRealizedPnl().compareTo(sp.getProfit()));
        // (150-100)*10 = 500.
        assertEquals(0, new BigDecimal("500.00").compareTo(sp.getProfit().setScale(2, RoundingMode.HALF_UP)));
    }

    @Test
    @WithMockUser(username = "user")
    void editingSellIntoBuyRemovesSoldPosition() {
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, new BigDecimal("20"), new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy");
        Transaction sell = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.SELL, BigDecimal.TEN, new BigDecimal("130.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 6, 1), "Sell");
        assertEquals(1, soldPositionRepository.count());

        // Change the transaction from a SELL into a BUY — the sold position must disappear.
        transactionService.update(
                sell.getId(), asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, BigDecimal.TEN, new BigDecimal("130.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 6, 1), "Now a buy", null);

        assertTrue(soldPositionRepository.findBySourceTransactionId(sell.getId()).isEmpty());
        assertEquals(0, soldPositionRepository.count());
    }

    @Test
    @WithMockUser(username = "user")
    void deletingSellRemovesSoldPosition() {
        transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.BUY, new BigDecimal("20"), new BigDecimal("100.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 1, 1), "Buy");
        Transaction sell = transactionService.create(
                asset.getId(), account.getId(), owner.getId(),
                TransactionType.SELL, BigDecimal.TEN, new BigDecimal("130.00"),
                BigDecimal.ZERO, "USD", LocalDate.of(2024, 6, 1), "Sell");
        assertEquals(1, soldPositionRepository.count());

        transactionService.delete(sell.getId());

        assertEquals(0, soldPositionRepository.count());
    }
}
