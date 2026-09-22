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
 * Verifies {@link IbkrSyncService} classifies incoming Flex trades against existing data:
 * NEW (nothing matches), DUPLICATE (same tradeID already synced, or a matching hand-entered row),
 * and MISMATCH (a matching row with different values). Apply must be idempotent on re-run.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IbkrSyncServiceTest {

    @Autowired private IbkrSyncService syncService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private SoldPositionRepository soldPositionRepository;
    @Autowired private AppUserRepository appUserRepository;

    private Long userId;
    private Account account;
    private Owner owner;

    @BeforeEach
    void setup() {
        soldPositionRepository.deleteAll();
        transactionRepository.deleteAll();
        holdingRepository.deleteAll();
        assetRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();
        AppUser user = appUserRepository.findByUsername("syncuser").orElseGet(() ->
                appUserRepository.save(AppUser.builder().username("syncuser").email("s@t.com")
                        .password("x").displayName("S").role("USER").build()));
        userId = user.getId();
        owner = ownerRepository.save(Owner.builder().name("Mugu").relationship(OwnerRelationship.SELF).userId(userId).build());
        account = accountRepository.save(Account.builder().name("IBKR").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(userId).build());
    }

    private String flex(String tradeId, String symbol, String buySell, String qty, String price, String date) {
        return "<FlexQueryResponse><FlexStatements><FlexStatement><Trades>"
                + "<Trade tradeID=\"" + tradeId + "\" symbol=\"" + symbol + "\" assetCategory=\"STK\" buySell=\""
                + buySell + "\" quantity=\"" + qty + "\" tradePrice=\"" + price + "\" currency=\"USD\" tradeDate=\""
                + date + "\" ibCommission=\"-1.00\"/>"
                + "</Trades></FlexStatement></FlexStatements></FlexQueryResponse>";
    }

    @Test
    @WithMockUser(username = "syncuser")
    void classifiesNewTradeAndApplyInsertsThenIsIdempotent() {
        String xml = flex("55501", "AAPL", "BUY", "10", "150.00", "20240115");

        var preview = syncService.preview(xml, userId, account, owner, null, null);
        assertEquals(1, preview.newTrades().size());
        assertEquals(0, preview.duplicates().size());
        assertEquals(0, preview.mismatches().size());

        var result = syncService.apply(xml, userId, account, owner, null, null, java.util.Set.of());
        assertEquals(1, result.inserted());
        assertEquals(1, result.assetsCreated(), "AAPL auto-created");
        Transaction saved = transactionRepository.findByUserIdAndExternalId(userId, "55501").orElseThrow();
        assertEquals("AAPL", saved.getAsset().getSymbol());

        // Re-running the same statement must not create a second transaction.
        var second = syncService.apply(xml, userId, account, owner, null, null, java.util.Set.of());
        assertEquals(0, second.inserted(), "already-synced trade is skipped");
        assertEquals(1, transactionRepository.findByUserIdOrderByTransactionDateDesc(userId).size());
    }

    @Test
    @WithMockUser(username = "syncuser")
    void detectsMismatchAgainstHandEnteredTransactionAndOverwritesOnlyWhenApproved() {
        Asset asset = assetRepository.save(Asset.builder().userId(userId).name("AAPL").symbol("AAPL")
                .assetType(AssetType.GROWTH_EQUITY).currency(Currency.USD).build());
        // Hand-entered (no externalId): same date/qty/type but a slightly different price.
        transactionRepository.save(Transaction.builder().userId(userId).asset(asset).account(account).owner(owner)
                .transactionType(TransactionType.BUY).quantity(new BigDecimal("10")).pricePerUnit(new BigDecimal("149.00"))
                .totalAmount(new BigDecimal("1490.00")).fees(BigDecimal.ZERO).currency(Currency.USD)
                .transactionDate(LocalDate.of(2024, 1, 15)).build());

        String xml = flex("55502", "AAPL", "BUY", "10", "150.00", "20240115");

        var preview = syncService.preview(xml, userId, account, owner, null, null);
        assertEquals(0, preview.newTrades().size());
        assertEquals(1, preview.mismatches().size(), "matched hand-entered row with a different price");
        assertTrue(preview.mismatches().get(0).mismatchDetail().contains("price"));

        // Not approved → the user's value is left untouched and the mismatch stays visible (not
        // stamped as synced), so the user can still decide later.
        var r1 = syncService.apply(xml, userId, account, owner, null, null, java.util.Set.of());
        assertEquals(0, r1.updated());
        assertEquals(1, transactionRepository.findByUserIdOrderByTransactionDateDesc(userId).size(), "no new row inserted");
        Transaction after = transactionRepository.findByUserIdOrderByTransactionDateDesc(userId).get(0);
        assertNull(after.getExternalId(), "unapproved mismatch is not stamped");
        assertEquals(0, new BigDecimal("149.00").compareTo(after.getPricePerUnit()), "unapproved mismatch not overwritten");

        // Approved → overwrite with IBKR's price and stamp the tradeID.
        var r2 = syncService.apply(xml, userId, account, owner, null, null, java.util.Set.of("55502"));
        assertEquals(1, r2.updated());
        Transaction overwritten = transactionRepository.findByUserIdAndExternalId(userId, "55502").orElseThrow();
        assertEquals(0, new BigDecimal("150.00").compareTo(overwritten.getPricePerUnit()), "approved mismatch overwritten");
    }

    @Test
    @WithMockUser(username = "syncuser")
    void importsTradesFromTransactionHistoryCsvFileAndIsIdempotent() {
        // The IBKR "Transaction History" CSV has no trade id, so dedupe is by the fuzzy match.
        String csv = String.join("\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount",
            "Transaction History,Data,2025-01-10,U1,SINGTEL,Buy,Z74,900.0,3.05,SGD,-2745.0,-2.5,-2747.725",
            "Transaction History,Data,2025-01-06,U1,Electronic Fund Transfer,Deposit,-,-,-,-,10000.0,-,10000.0");
        byte[] file = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        var preview = syncService.previewFile(file, userId, account, owner, null, null);
        assertEquals(1, preview.newTrades().size(), "only the Z74 Buy is a new trade; the deposit is ignored");

        var result = syncService.applyFile(file, userId, account, owner, null, null, java.util.Set.of());
        assertEquals(1, result.inserted());
        assertEquals(1, transactionRepository.findByUserIdOrderByTransactionDateDesc(userId).size());
        Transaction tx = transactionRepository.findByUserIdOrderByTransactionDateDesc(userId).get(0);
        assertEquals("Z74", tx.getAsset().getSymbol());
        assertEquals(com.myfinance.model.enums.Currency.SGD, tx.getCurrency(), "trade currency from Price Currency column");
        assertEquals(0, new BigDecimal("3.05").compareTo(tx.getPricePerUnit()));

        // Re-importing the same file must not duplicate the trade (fuzzy match recognises it).
        var second = syncService.applyFile(file, userId, account, owner, null, null, java.util.Set.of());
        assertEquals(0, second.inserted(), "the hand-entered/previously-imported trade is recognised");
        assertEquals(1, transactionRepository.findByUserIdOrderByTransactionDateDesc(userId).size(), "no duplicate row");
    }

    @Test
    @WithMockUser(username = "syncuser")
    void appliesTradesChronologicallySoASellListedBeforeItsBuyStillImports() {
        // A Tiger statement listing the SELL row physically BEFORE the earlier BUY row. Without
        // chronological ordering the SELL would hit an empty holding ("Cannot sell more than held").
        String csv = String.join("\n",
            "Activity Statement,,,,2024-01-01 - 2024-12-31",
            "Account Information,,,,Account,Address,Account Category,Base Currency",
            "Account Information,,,DATA,50414420,ADDR,Cash,USD",
            "Trades,,,,Symbol,Market,Exchange,Activity Type,Quantity,Trade Price,Amount,Commission,Trade Time,Settle Date,Currency",
            "Trades,Stock,,DATA,AAPL,US,,Sell,4,200.00000,800.00,-1.00,\"2024-06-10\n10:00:00, US/Eastern\",,USD",
            "Trades,Stock,,DATA,AAPL,US,,,10,150.00000,1500.00,-1.00,\"2024-01-15\n10:00:00, US/Eastern\",,USD");
        byte[] file = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        var result = syncService.applyFile(file, userId, account, owner, null, null, java.util.Set.of());
        assertEquals(2, result.inserted(), "both the buy and the sell import despite the file's order");

        // Net holding = 10 bought - 4 sold = 6.
        var holding = holdingRepository.findAll().stream()
                .filter(h -> "AAPL".equals(h.getAsset().getSymbol())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("6").compareTo(holding.getQuantity()), "6 shares remain after the earlier buy and later sell");
    }
}
