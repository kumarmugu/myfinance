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
