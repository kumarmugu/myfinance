package com.myfinance.service;

import com.myfinance.model.*;
import com.myfinance.model.enums.*;
import com.myfinance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link StockSplitService}. Uses real repositories so the split's effect on the
 * stored transactions and the derived holding is verified end-to-end. Verifies the core invariant of a
 * split: share count scales, per-share price scales inversely, and the total cost basis is unchanged.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StockSplitServiceTest {

    @Autowired private StockSplitService stockSplitService;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private AppUser user;
    private Owner owner;
    private Account account;

    @BeforeEach
    void setup() {
        holdingRepository.deleteAll();
        transactionRepository.deleteAll();
        assetRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();

        user = appUserRepository.findByUsername("split-user").orElseGet(() ->
                appUserRepository.save(AppUser.builder()
                        .username("split-user").email("split@test.com")
                        .password(passwordEncoder.encode("test123"))
                        .displayName("Split User").role("USER").build()));
        owner = ownerRepository.save(Owner.builder()
                .name("Owner").relationship(OwnerRelationship.SELF).userId(user.getId()).build());
        account = accountRepository.save(Account.builder()
                .name("Broker").accountType(AccountType.BROKER).currency(Currency.USD)
                .owner(owner).userId(user.getId()).build());
    }

    private Asset asset(String symbol) {
        return assetRepository.save(Asset.builder()
                .name(symbol).symbol(symbol).assetType(AssetType.GROWTH_EQUITY)
                .currency(Currency.USD).userId(user.getId()).build());
    }

    private Transaction buy(Asset a, String qty, String price, LocalDate date) {
        BigDecimal q = new BigDecimal(qty), p = new BigDecimal(price);
        return transactionRepository.save(Transaction.builder()
                .asset(a).account(account).owner(owner).transactionType(TransactionType.BUY)
                .quantity(q).pricePerUnit(p).totalAmount(q.multiply(p)).fees(BigDecimal.ZERO)
                .currency(Currency.USD).transactionDate(date).userId(user.getId()).build());
    }

    private Holding holding(Asset a, String qty, String avgPrice, String invested) {
        return holdingRepository.save(Holding.builder()
                .asset(a).account(account).owner(owner)
                .quantity(new BigDecimal(qty)).averageBuyPrice(new BigDecimal(avgPrice))
                .investedAmount(new BigDecimal(invested)).currency(Currency.USD)
                .userId(user.getId()).build());
    }

    @Test
    void forwardSplitScalesQuantityUpAndPriceDownKeepingCostBasis() {
        Asset tsla = asset("TSLA");
        // 5 shares @ 900 = 4500 invested, held 5 @ 900.
        Transaction b = buy(tsla, "5", "900", LocalDate.of(2022, 1, 10));
        Holding h = holding(tsla, "5", "900", "4500");

        // 3-for-1 forward split effective 2022-08-25.
        StockSplitService.SplitResult r = stockSplitService.applySplit(
                user.getId(), "TSLA", LocalDate.of(2022, 8, 25), 3, 1);

        assertEquals(1, r.transactionsAdjusted());
        assertEquals(1, r.holdingsAdjusted());

        Transaction adjusted = transactionRepository.findById(b.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("15").compareTo(adjusted.getQuantity()), "5 × 3 = 15 shares");
        assertEquals(0, new BigDecimal("300").compareTo(adjusted.getPricePerUnit()), "900 ÷ 3 = 300");
        assertEquals(0, new BigDecimal("4500").compareTo(adjusted.getTotalAmount()), "cash total unchanged");

        Holding hh = holdingRepository.findById(h.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("15").compareTo(hh.getQuantity()));
        assertEquals(0, new BigDecimal("300").compareTo(hh.getAverageBuyPrice()));
        assertEquals(0, new BigDecimal("4500").compareTo(hh.getInvestedAmount()), "cost basis unchanged");
    }

    @Test
    void reverseSplitScalesQuantityDownAndPriceUp() {
        Asset x = asset("TQQQ");
        Transaction b = buy(x, "80", "10", LocalDate.of(2021, 1, 1)); // 800 invested
        Holding h = holding(x, "80", "10", "800");

        // 1-for-8 reverse split.
        stockSplitService.applySplit(user.getId(), "TQQQ", LocalDate.of(2021, 6, 1), 1, 8);

        Transaction adjusted = transactionRepository.findById(b.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("10").compareTo(adjusted.getQuantity()), "80 × (1/8) = 10");
        assertEquals(0, new BigDecimal("80").compareTo(adjusted.getPricePerUnit()), "10 ÷ (1/8) = 80");

        Holding hh = holdingRepository.findById(h.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("10").compareTo(hh.getQuantity()));
        assertEquals(0, new BigDecimal("800").compareTo(hh.getInvestedAmount()), "cost basis unchanged");
    }

    @Test
    void onlyAdjustsTransactionsDatedOnOrBeforeEffectiveDate() {
        Asset a = asset("AAA");
        Transaction before = buy(a, "10", "100", LocalDate.of(2022, 8, 24)); // adjusted
        Transaction onDate = buy(a, "10", "100", LocalDate.of(2022, 8, 25)); // adjusted (inclusive)
        Transaction after  = buy(a, "10", "100", LocalDate.of(2022, 8, 26)); // NOT adjusted
        holding(a, "30", "100", "3000");

        StockSplitService.SplitResult r = stockSplitService.applySplit(
                user.getId(), "AAA", LocalDate.of(2022, 8, 25), 2, 1);

        assertEquals(2, r.transactionsAdjusted(), "only on/before the effective date");
        assertEquals(0, new BigDecimal("20").compareTo(transactionRepository.findById(before.getId()).orElseThrow().getQuantity()));
        assertEquals(0, new BigDecimal("20").compareTo(transactionRepository.findById(onDate.getId()).orElseThrow().getQuantity()));
        assertEquals(0, new BigDecimal("10").compareTo(transactionRepository.findById(after.getId()).orElseThrow().getQuantity()), "post-split trade untouched");
    }

    @Test
    void throwsWhenNoHoldingForSymbol() {
        asset("AAA");
        assertThrows(RuntimeException.class, () ->
                stockSplitService.applySplit(user.getId(), "ZZZ", LocalDate.of(2022, 8, 25), 3, 1));
    }

    @Test
    void rejectsNonPositiveRatio() {
        asset("AAA");
        assertThrows(RuntimeException.class, () ->
                stockSplitService.applySplit(user.getId(), "AAA", LocalDate.of(2022, 8, 25), 0, 1));
        assertThrows(RuntimeException.class, () ->
                stockSplitService.applySplit(user.getId(), "AAA", LocalDate.of(2022, 8, 25), 3, 0));
    }

    @Test
    void doesNotTouchAnotherUsersAsset() {
        // Asset symbols are globally unique, so each ticker belongs to exactly one user. A split by our
        // user must only ever load OUR assets (findByUserId) — another user's asset with a different
        // ticker must never be swept in, and a split for their ticker must not resolve to their data.
        AppUser other = appUserRepository.findByUsername("split-other").orElseGet(() ->
                appUserRepository.save(AppUser.builder()
                        .username("split-other").email("o@test.com")
                        .password(passwordEncoder.encode("x")).displayName("Other").role("USER").build()));
        Owner otherOwner = ownerRepository.save(Owner.builder()
                .name("O").relationship(OwnerRelationship.SELF).userId(other.getId()).build());
        Account otherAcct = accountRepository.save(Account.builder()
                .name("OB").accountType(AccountType.BROKER).currency(Currency.USD)
                .owner(otherOwner).userId(other.getId()).build());
        Asset otherAsset = assetRepository.save(Asset.builder()
                .name("OTHERCO").symbol("OTHERCO").assetType(AssetType.GROWTH_EQUITY)
                .currency(Currency.USD).userId(other.getId()).build());
        Transaction otherTxn = transactionRepository.save(Transaction.builder()
                .asset(otherAsset).account(otherAcct).owner(otherOwner).transactionType(TransactionType.BUY)
                .quantity(new BigDecimal("5")).pricePerUnit(new BigDecimal("900"))
                .totalAmount(new BigDecimal("4500")).fees(BigDecimal.ZERO)
                .currency(Currency.USD).transactionDate(LocalDate.of(2022, 1, 10))
                .userId(other.getId()).build());

        // Our user holds TSLA.
        Asset mine = asset("TSLA");
        buy(mine, "5", "900", LocalDate.of(2022, 1, 10));
        holding(mine, "5", "900", "4500");

        // Splitting OUR TSLA must not touch the other user's OTHERCO.
        stockSplitService.applySplit(user.getId(), "TSLA", LocalDate.of(2022, 8, 25), 3, 1);
        Transaction untouched = transactionRepository.findById(otherTxn.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("5").compareTo(untouched.getQuantity()), "other user's shares unchanged");
        assertEquals(0, new BigDecimal("900").compareTo(untouched.getPricePerUnit()));

        // And our user cannot split the other user's ticker — it isn't in our asset list.
        assertThrows(RuntimeException.class, () ->
                stockSplitService.applySplit(user.getId(), "OTHERCO", LocalDate.of(2022, 8, 25), 3, 1));
        Transaction stillUntouched = transactionRepository.findById(otherTxn.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("5").compareTo(stillUntouched.getQuantity()));
    }
}
