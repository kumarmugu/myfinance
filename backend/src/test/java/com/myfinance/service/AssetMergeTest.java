package com.myfinance.service;

import com.myfinance.model.*;
import com.myfinance.model.enums.*;
import com.myfinance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link AssetService#mergeDuplicateAssets} folds import-created "NAME (TICKER)" duplicate
 * assets back into the canonical ticker, repointing dividends and deleting the duplicate.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssetMergeTest {

    @Autowired private AssetService assetService;
    @Autowired private AssetRepository assetRepository;
    @Autowired private DividendRepository dividendRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private AppUserRepository appUserRepository;

    private Long userId;
    private Account account;
    private Owner owner;

    @BeforeEach
    void setup() {
        dividendRepository.deleteAll();
        assetRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();
        AppUser user = appUserRepository.findByUsername("mergeuser").orElseGet(() ->
                appUserRepository.save(AppUser.builder().username("mergeuser").email("m@t.com")
                        .password("x").displayName("M").role("USER").build()));
        userId = user.getId();
        owner = ownerRepository.save(Owner.builder().name("Mugu").relationship(OwnerRelationship.SELF).userId(userId).build());
        account = accountRepository.save(Account.builder().name("Tiger").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(userId).build());
    }

    @Test
    void mergesDescriptiveDuplicateIntoCanonicalTickerAndRepointsDividends() {
        Asset canonical = assetRepository.save(Asset.builder().userId(userId).name("Meta").symbol("META")
                .assetType(AssetType.GROWTH_EQUITY).currency(Currency.USD).build());
        Asset dup = assetRepository.save(Asset.builder().userId(userId)
                .name("META PLATFORMS, INC. (META)").symbol("META PLATFORMS, INC. (META)")
                .assetType(AssetType.OTHER).currency(Currency.USD).build());
        dividendRepository.save(Dividend.builder().userId(userId).asset(dup).account(account).owner(owner)
                .amount(new BigDecimal("3.30")).currency(Currency.USD).receivedDate(LocalDate.of(2025, 12, 23))
                .instrument("META PLATFORMS, INC. (META)").build());

        AssetService.MergeResult r = assetService.mergeDuplicateAssets(userId);

        assertEquals(1, r.assetsMerged());
        assertEquals(1, r.dividendsRepointed());
        assertFalse(assetRepository.findById(dup.getId()).isPresent(), "duplicate asset deleted");
        assertTrue(assetRepository.findById(canonical.getId()).isPresent(), "canonical kept");
        assertEquals(1, dividendRepository.findByAssetId(canonical.getId()).size(), "dividend repointed to META");
    }

    @Test
    void leavesGenuineAssetsUntouchedAndIsIdempotent() {
        assetRepository.save(Asset.builder().userId(userId).name("Apple").symbol("AAPL")
                .assetType(AssetType.GROWTH_EQUITY).currency(Currency.USD).build());
        assertEquals(0, assetService.mergeDuplicateAssets(userId).assetsMerged());
        assertEquals(0, assetService.mergeDuplicateAssets(userId).assetsMerged(), "idempotent");
        assertEquals(1, assetRepository.findByUserId(userId).size());
    }
}
