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
 * Tests {@link AssetService#mergeInto}: folding one asset (e.g. an imported FB) into another (the
 * renamed META) must move transactions/holdings/dividends, record the old ticker as a previous symbol
 * of the survivor, merge overlapping holdings, and delete the source — all tenant-scoped.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssetServiceTest {

    @Autowired private AssetService assetService;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private DividendRepository dividendRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long userId;
    private Owner owner;
    private Account account;

    @BeforeEach
    void setup() {
        dividendRepository.deleteAll();
        holdingRepository.deleteAll();
        transactionRepository.deleteAll();
        assetRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();
        AppUser user = appUserRepository.findByUsername("assetuser").orElseGet(() ->
                appUserRepository.save(AppUser.builder().username("assetuser").email("a@t.com")
                        .password(passwordEncoder.encode("x")).displayName("A").role("USER").build()));
        userId = user.getId();
        owner = ownerRepository.save(Owner.builder().name("O").relationship(OwnerRelationship.SELF).userId(userId).build());
        account = accountRepository.save(Account.builder().name("B").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(userId).build());
    }

    private Asset asset(String symbol) {
        return assetRepository.save(Asset.builder().userId(userId).name(symbol).symbol(symbol)
                .assetType(AssetType.GROWTH_EQUITY).currency(Currency.USD).build());
    }

    private void txn(Asset a, String qty, String price) {
        BigDecimal q = new BigDecimal(qty), p = new BigDecimal(price);
        transactionRepository.save(Transaction.builder().asset(a).account(account).owner(owner)
                .transactionType(TransactionType.BUY).quantity(q).pricePerUnit(p).totalAmount(q.multiply(p))
                .fees(BigDecimal.ZERO).currency(Currency.USD).transactionDate(LocalDate.of(2022, 1, 1))
                .userId(userId).build());
    }

    private Holding holding(Asset a, String qty, String avg, String invested) {
        return holdingRepository.save(Holding.builder().asset(a).account(account).owner(owner)
                .quantity(new BigDecimal(qty)).averageBuyPrice(new BigDecimal(avg))
                .investedAmount(new BigDecimal(invested)).currency(Currency.USD).userId(userId).build());
    }

    @Test
    void mergeIntoFoldsSourceAndRecordsPreviousSymbol() {
        Asset fb = asset("FB");
        Asset meta = asset("META");
        txn(fb, "5", "200");                 // 1 transaction on FB
        holding(fb, "5", "200", "1000");     // FB holding, no overlapping META holding

        var r = assetService.mergeInto(userId, fb.getId(), meta.getId());

        assertEquals("META", r.survivingSymbol());
        assertEquals("FB", r.mergedSymbol());
        assertEquals(1, r.transactionsRepointed());
        assertEquals(1, r.holdingsMerged());
        assertTrue(assetRepository.findById(fb.getId()).isEmpty(), "FB deleted");

        Asset survivor = assetRepository.findById(meta.getId()).orElseThrow();
        assertTrue(survivor.getPreviousSymbols() != null && survivor.getPreviousSymbols().contains("FB"),
                "FB recorded as a previous symbol of META");
        // The FB transaction now points at META.
        assertEquals(1, transactionRepository.findByAssetIdOrderByTransactionDateDesc(meta.getId()).size());
        assertEquals(0, transactionRepository.findByAssetIdOrderByTransactionDateDesc(fb.getId()).size());
    }

    @Test
    void mergeIntoCombinesOverlappingHoldings() {
        Asset fb = asset("FB");
        Asset meta = asset("META");
        holding(fb, "5", "200", "1000");     // 5 @ 200
        holding(meta, "10", "300", "3000");  // 10 @ 300 — same account+owner → should merge

        var r = assetService.mergeInto(userId, fb.getId(), meta.getId());
        assertEquals(1, r.holdingsMerged());

        var metaHoldings = holdingRepository.findByAssetId(meta.getId());
        assertEquals(1, metaHoldings.size(), "single merged holding");
        Holding h = metaHoldings.get(0);
        assertEquals(0, new BigDecimal("15").compareTo(h.getQuantity()), "5 + 10 = 15");
        assertEquals(0, new BigDecimal("4000").compareTo(h.getInvestedAmount()), "1000 + 3000 = 4000 cost basis");
        assertEquals(0, holdingRepository.findByAssetId(fb.getId()).size(), "FB holding removed");
    }

    @Test
    void mergeIntoRejectsAnotherUsersAsset() {
        Asset fb = asset("FB");
        AppUser other = appUserRepository.findByUsername("assetuser-other").orElseGet(() ->
                appUserRepository.save(AppUser.builder().username("assetuser-other").email("o@t.com")
                        .password(passwordEncoder.encode("x")).displayName("O").role("USER").build()));
        Asset foreign = assetRepository.save(Asset.builder().userId(other.getId()).name("X").symbol("XSYM")
                .assetType(AssetType.GROWTH_EQUITY).currency(Currency.USD).build());

        assertThrows(RuntimeException.class, () -> assetService.mergeInto(userId, fb.getId(), foreign.getId()));
        assertThrows(RuntimeException.class, () -> assetService.mergeInto(userId, fb.getId(), fb.getId()),
                "cannot merge an asset into itself");
    }
}
