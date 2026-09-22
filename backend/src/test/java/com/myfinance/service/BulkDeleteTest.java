package com.myfinance.service;

import com.myfinance.model.*;
import com.myfinance.model.enums.*;
import com.myfinance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies scoped bulk-delete of dividends and transactions: only the chosen owner+account rows are
 * removed (others untouched), and a transaction bulk-delete also resets the derived holdings and
 * sold positions for that scope.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BulkDeleteTest {

    @Autowired private DividendService dividendService;
    @Autowired private TransactionService transactionService;
    @Autowired private DividendRepository dividendRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private SoldPositionRepository soldPositionRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private AppUserRepository appUserRepository;

    private Long userId;
    private Owner owner, otherOwner;
    private Account acct, otherAcct;
    private Asset asset;

    @BeforeEach
    void setup() {
        dividendRepository.deleteAll();
        soldPositionRepository.deleteAll();
        holdingRepository.deleteAll();
        transactionRepository.deleteAll();
        assetRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();
        AppUser user = appUserRepository.findByUsername("bulkuser").orElseGet(() ->
                appUserRepository.save(AppUser.builder().username("bulkuser").email("b@t.com")
                        .password("x").displayName("B").role("USER").build()));
        userId = user.getId();
        owner = ownerRepository.save(Owner.builder().name("Mugu").relationship(OwnerRelationship.SELF).userId(userId).build());
        otherOwner = ownerRepository.save(Owner.builder().name("Vaish").relationship(OwnerRelationship.SPOUSE).userId(userId).build());
        acct = accountRepository.save(Account.builder().name("IBKR").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(userId).build());
        otherAcct = accountRepository.save(Account.builder().name("Tiger").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(userId).build());
        asset = assetRepository.save(Asset.builder().userId(userId).name("AAPL").symbol("AAPL")
                .assetType(AssetType.GROWTH_EQUITY).currency(Currency.USD).build());
    }

    private void div(Account a, Owner o) {
        dividendRepository.save(Dividend.builder().userId(userId).asset(asset).account(a).owner(o)
                .amount(new BigDecimal("5.00")).currency(Currency.USD).receivedDate(LocalDate.of(2025, 1, 1))
                .instrument("AAPL").build());
    }

    @Test
    void dividendBulkDeleteRemovesOnlyTheChosenOwnerAndAccount() {
        div(acct, owner);          // target
        div(acct, owner);          // target
        div(acct, otherOwner);     // same account, different owner → keep
        div(otherAcct, owner);     // same owner, different account → keep

        assertEquals(2, dividendService.countForOwnerAccount(userId, owner.getId(), acct.getId()));
        int deleted = dividendService.deleteForOwnerAccount(userId, owner.getId(), acct.getId());

        assertEquals(2, deleted);
        assertEquals(2, dividendRepository.findByUserIdOrderByReceivedDateDesc(userId).size(), "the other owner/account rows remain");
    }

    @Test
    @WithMockUser(username = "bulkuser")
    void transactionBulkDeleteAlsoResetsHoldingsAndSoldPositions() {
        // A buy then a partial sell on the target account (creates a holding + a sold position).
        transactionService.create(asset.getId(), acct.getId(), owner.getId(), TransactionType.BUY,
                new BigDecimal("10"), new BigDecimal("100"), BigDecimal.ZERO, "USD",
                LocalDate.of(2025, 1, 1), "buy", InvestmentPurpose.LONG_TERM, null, null);
        transactionService.create(asset.getId(), acct.getId(), owner.getId(), TransactionType.SELL,
                new BigDecimal("4"), new BigDecimal("120"), BigDecimal.ZERO, "USD",
                LocalDate.of(2025, 6, 1), "sell", InvestmentPurpose.LONG_TERM, null, null);
        // An unrelated buy on the other account for the same owner — must survive.
        transactionService.create(asset.getId(), otherAcct.getId(), owner.getId(), TransactionType.BUY,
                new BigDecimal("3"), new BigDecimal("90"), BigDecimal.ZERO, "USD",
                LocalDate.of(2025, 2, 1), "buy", InvestmentPurpose.LONG_TERM, null, null);

        var preview = transactionService.countForOwnerAccount(userId, owner.getId(), acct.getId());
        assertEquals(2, preview.transactions(), "the 2 target-account trades");
        assertEquals(1, preview.holdings());
        assertEquals(1, preview.soldPositions());

        var result = transactionService.deleteForOwnerAccount(userId, owner.getId(), acct.getId());
        assertEquals(2, result.transactions());
        assertEquals(1, result.holdings());
        assertEquals(1, result.soldPositions());

        assertEquals(0, transactionRepository.findByUserIdAndAccountIdOrderByTransactionDateDesc(userId, acct.getId()).size());
        assertTrue(holdingRepository.findByAccountId(acct.getId()).isEmpty(), "target holdings removed");
        assertTrue(soldPositionRepository.findByAccountIdOrderBySoldDateDesc(acct.getId()).isEmpty(), "target sold positions removed");
        // The other account's trade + holding are untouched.
        assertEquals(1, transactionRepository.findByUserIdAndAccountIdOrderByTransactionDateDesc(userId, otherAcct.getId()).size());
        assertFalse(holdingRepository.findByAccountId(otherAcct.getId()).isEmpty(), "other account's holding survives");
    }
}
