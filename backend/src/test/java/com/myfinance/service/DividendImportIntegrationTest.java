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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the full dividend import path, focused on idempotency: importing the same
 * statement twice must not create duplicate dividend rows.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DividendImportIntegrationTest {

    @Autowired private DividendImportService importService;
    @Autowired private DividendRepository dividendRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private AppUser user;
    private Account account;
    private Owner owner;

    private static final String CSV = String.join("\n",
        "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount",
        "Transaction History,Data,2026-08-19,U1,Z74(SG1T75931496) Cash Dividend SGD 0.103 per Share (Ordinary Dividend),Dividend,Z74,-,-,-,-,-,92.7",
        "Transaction History,Data,2026-08-13,U1,AAPL(US0378331005) Cash Dividend USD 0.27 per Share (Ordinary Dividend),Dividend,AAPL,-,-,-,-,-,6.91524",
        "Transaction History,Data,2026-08-13,U1,AAPL(US0378331005) Cash Dividend USD 0.27 per Share - US Tax,Foreign Tax Withholding,AAPL,-,-,-,-,-,-2.074572",
        "Transaction History,Data,2026-01-06,U1,Electronic Fund Transfer,Deposit,-,-,-,-,-,-,10000.0");

    @BeforeEach
    void setup() {
        dividendRepository.deleteAll();
        assetRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();
        user = appUserRepository.findByUsername("user").orElseGet(() -> appUserRepository.save(
                AppUser.builder().username("user").email("u@t.com").password(passwordEncoder.encode("x"))
                        .displayName("U").role("USER").build()));
        owner = ownerRepository.save(Owner.builder().name("Mugu").relationship(OwnerRelationship.SELF).userId(user.getId()).build());
        account = accountRepository.save(Account.builder().name("IBKR").accountType(AccountType.BROKER)
                .currency(Currency.SGD).owner(owner).userId(user.getId()).build());
    }

    private static final String TIGER_CSV = String.join("\n",
        "Activity Statement,,,,2022-01-01 - 2022-12-31",
        "Dividends,,,,Date,Product,Symbol,Dividend Reinvestment Plan,Quantity/Gross Rate,Phase,Cash Dividends,Shares,Fees & Tax,Net Cash Value,Currency",
        "Dividends,,,DATA,2022-03-29,,VOO,,,Paid,1.37,0,,1.37,USD",
        "Dividends,,,DATA,2022-05-12,,AAPL,,,Paid,1.15,0,,1.15,USD",
        "Dividends,,,DATA,2022-12-22,,TQQQ,,Quantity: 24,Dividend Accruals Increase,2.35,0,Fee: 0.71,1.64,USD");

    @Test
    @WithMockUser(username = "user")
    void reimportingSameTigerFileSkipsDuplicates() {
        var first = importService.importFile(TIGER_CSV.getBytes(StandardCharsets.UTF_8),
                DividendImportService.Format.TIGER_CSV, user.getId(), account, owner);
        assertEquals(2, first.imported());   // 2 Paid; TQQQ accrual excluded
        assertEquals(0, first.skipped());

        var second = importService.importFile(TIGER_CSV.getBytes(StandardCharsets.UTF_8),
                DividendImportService.Format.TIGER_CSV, user.getId(), account, owner);
        assertEquals(0, second.imported(), "re-import must create nothing");
        assertEquals(2, second.skipped(), "both Paid rows recognised as duplicates");
        assertEquals(2, dividendRepository.findByUserIdOrderByReceivedDateDesc(user.getId()).size());
    }

    @Test
    @WithMockUser(username = "user")
    void descriptiveInstrumentNameReusesExistingAssetByNameInsteadOfDuplicating() {
        // The user already has a Microsoft asset (symbol MSFT). A statement whose instrument column is
        // the descriptive name "MICROSOFT CORP." must reuse it, not create a duplicate asset.
        assetRepository.save(Asset.builder().userId(user.getId()).name("Microsoft Corp").symbol("MSFT")
                .assetType(AssetType.GROWTH_EQUITY).currency(Currency.USD).build());
        long assetsBefore = assetRepository.count();

        String tiger = String.join("\n",
            "Activity Statement,,,,2024",
            "Dividends,,,,Date,Product,Symbol,Dividend Reinvestment Plan,Quantity/Gross Rate,Phase,Cash Dividends,Shares,Fees & Tax,Net Cash Value,Currency",
            "Dividends,,,DATA,2024-06-10,,MICROSOFT CORP.,,,Paid,3.00,0,,3.00,USD");
        var res = importService.importFile(tiger.getBytes(StandardCharsets.UTF_8),
                DividendImportService.Format.TIGER_CSV, user.getId(), account, owner);

        assertEquals(1, res.imported());
        assertEquals(0, res.assetsCreated(), "must reuse the existing Microsoft asset, not create a new one");
        assertEquals(assetsBefore, assetRepository.count(), "no duplicate asset created");
        // The dividend is linked to the existing MSFT asset.
        Dividend d = dividendRepository.findByUserIdOrderByReceivedDateDesc(user.getId()).get(0);
        assertEquals("MSFT", d.getAsset().getSymbol());
    }

    @Test
    @WithMockUser(username = "user")
    void sameDistributionFromTwoSourcesReconcilesToTheFullerAmountWithoutDuplicating() {
        // Simulate the IBKR live-fetch reporting only the ordinary component (14.85) first, then the
        // CSV file reporting the full REIT distribution (83.97) for the same symbol+date+account+owner.
        // Result: ONE dividend, corrected up to the fuller total — never two rows with different amounts.
        String flexPartial = String.join("\n",
            "Activity Statement,,,,2026",
            "Dividends,,,,Date,Product,Symbol,Dividend Reinvestment Plan,Quantity/Gross Rate,Phase,Cash Dividends,Shares,Fees & Tax,Net Cash Value,Currency",
            "Dividends,,,DATA,2026-09-07,,ME8U,,,Paid,14.85,0,,14.85,SGD");
        var r1 = importService.importFile(flexPartial.getBytes(StandardCharsets.UTF_8),
                DividendImportService.Format.TIGER_CSV, user.getId(), account, owner);
        assertEquals(1, r1.imported());

        String csvFull = String.join("\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount",
            "Transaction History,Data,2026-09-07,U1,ME8U (Ordinary Dividend),Dividend,ME8U,-,-,-,-,-,14.85",
            "Transaction History,Data,2026-09-07,U1,ME8U (Return of Capital),Dividend,ME8U,-,-,-,-,-,7.02",
            "Transaction History,Data,2026-09-07,U1,ME8U (Capital Gains),Dividend,ME8U,-,-,-,-,-,62.10");
        var r2 = importService.importFile(csvFull.getBytes(StandardCharsets.UTF_8),
                DividendImportService.Format.IBKR_CSV, user.getId(), account, owner);
        assertEquals(0, r2.imported(), "no new dividend row is created for the same distribution");

        var all = dividendRepository.findByUserIdOrderByReceivedDateDesc(user.getId());
        assertEquals(1, all.size(), "still exactly one ME8U dividend");
        assertEquals(0, new java.math.BigDecimal("83.97").compareTo(all.get(0).getAmount()),
                "reconciled to the fuller CSV total");

        // Importing the partial source again must NOT shrink it back.
        importService.importFile(flexPartial.getBytes(StandardCharsets.UTF_8),
                DividendImportService.Format.TIGER_CSV, user.getId(), account, owner);
        assertEquals(0, new java.math.BigDecimal("83.97").compareTo(
                dividendRepository.findByUserIdOrderByReceivedDateDesc(user.getId()).get(0).getAmount()),
                "a smaller/partial re-import does not reduce the stored total");
    }

    @Test
    @WithMockUser(username = "user")
    void reimportingSameFileSkipsDuplicates() {
        var first = importService.importFile(CSV.getBytes(StandardCharsets.UTF_8),
                DividendImportService.Format.IBKR_CSV, user.getId(), account, owner);
        // 2 dividend rows (Z74, AAPL); deposit + tax rows excluded.
        assertEquals(2, first.imported());
        assertEquals(0, first.skipped());
        assertEquals(2, dividendRepository.findByUserIdOrderByReceivedDateDesc(user.getId()).size());

        var second = importService.importFile(CSV.getBytes(StandardCharsets.UTF_8),
                DividendImportService.Format.IBKR_CSV, user.getId(), account, owner);
        assertEquals(0, second.imported(), "re-import must create nothing");
        assertEquals(2, second.skipped(), "both rows recognised as duplicates");
        assertEquals(2, dividendRepository.findByUserIdOrderByReceivedDateDesc(user.getId()).size(),
                "no duplicate rows after re-import");
    }
}
