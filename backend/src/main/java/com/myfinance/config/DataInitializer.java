package com.myfinance.config;

import com.myfinance.model.Account;
import com.myfinance.model.Asset;
import com.myfinance.model.enums.AccountType;
import com.myfinance.model.enums.AssetType;
import com.myfinance.model.enums.TransactionType;
import com.myfinance.repository.AccountRepository;
import com.myfinance.repository.AssetRepository;
import com.myfinance.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final AssetRepository assetRepository;
    private final TransactionService transactionService;

    @Override
    public void run(String... args) {
        if (accountRepository.count() > 0) return; // Skip if data exists

        // Create accounts (brokers and banks)
        Account zerodha = accountRepository.save(Account.builder()
                .name("Zerodha").accountType(AccountType.BROKER).description("Primary stock broker").build());
        Account groww = accountRepository.save(Account.builder()
                .name("Groww").accountType(AccountType.BROKER).description("Mutual fund broker").build());
        Account wazirx = accountRepository.save(Account.builder()
                .name("WazirX").accountType(AccountType.BROKER).description("Crypto exchange").build());
        Account sbi = accountRepository.save(Account.builder()
                .name("SBI Bank").accountType(AccountType.BANK).description("Savings and FD").build());
        Account hdfc = accountRepository.save(Account.builder()
                .name("HDFC Bank").accountType(AccountType.BANK).description("Savings account").build());

        // Create assets
        Asset reliance = assetRepository.save(Asset.builder()
                .name("Reliance Industries").symbol("RELIANCE").assetType(AssetType.EQUITY)
                .currentPrice(new BigDecimal("2850.50")).exchange("NSE").build());
        Asset tcs = assetRepository.save(Asset.builder()
                .name("Tata Consultancy Services").symbol("TCS").assetType(AssetType.EQUITY)
                .currentPrice(new BigDecimal("3920.75")).exchange("NSE").build());
        Asset infy = assetRepository.save(Asset.builder()
                .name("Infosys").symbol("INFY").assetType(AssetType.EQUITY)
                .currentPrice(new BigDecimal("1580.25")).exchange("NSE").build());
        Asset hdfcBank = assetRepository.save(Asset.builder()
                .name("HDFC Bank").symbol("HDFCBANK").assetType(AssetType.EQUITY)
                .currentPrice(new BigDecimal("1650.80")).exchange("NSE").build());

        Asset nifty50 = assetRepository.save(Asset.builder()
                .name("Nifty 50 Index Fund").symbol("NIFTY50-IDX").assetType(AssetType.INDEX_FUND)
                .currentPrice(new BigDecimal("245.30")).exchange("NSE").build());
        Asset sensex = assetRepository.save(Asset.builder()
                .name("Sensex Index Fund").symbol("SENSEX-IDX").assetType(AssetType.INDEX_FUND)
                .currentPrice(new BigDecimal("780.60")).exchange("BSE").build());

        Asset axisBlue = assetRepository.save(Asset.builder()
                .name("Axis Bluechip Fund").symbol("AXIS-BLUE").assetType(AssetType.MUTUAL_FUND)
                .currentPrice(new BigDecimal("52.40")).build());
        Asset miraeAsset = assetRepository.save(Asset.builder()
                .name("Mirae Asset Large Cap").symbol("MIRAE-LC").assetType(AssetType.MUTUAL_FUND)
                .currentPrice(new BigDecimal("95.80")).build());

        Asset btc = assetRepository.save(Asset.builder()
                .name("Bitcoin").symbol("BTC").assetType(AssetType.CRYPTO)
                .currentPrice(new BigDecimal("5500000")).exchange("WazirX").build());
        Asset eth = assetRepository.save(Asset.builder()
                .name("Ethereum").symbol("ETH").assetType(AssetType.CRYPTO)
                .currentPrice(new BigDecimal("250000")).exchange("WazirX").build());

        Asset sbiFD = assetRepository.save(Asset.builder()
                .name("SBI Fixed Deposit 7.1%").symbol("SBI-FD-71").assetType(AssetType.BANK_DEPOSIT)
                .currentPrice(BigDecimal.ONE).build());
        Asset hdfcSavings = assetRepository.save(Asset.builder()
                .name("HDFC Savings Account").symbol("HDFC-SAV").assetType(AssetType.BANK_DEPOSIT)
                .currentPrice(BigDecimal.ONE).build());

        // Create sample transactions
        transactionService.createTransaction(reliance.getId(), zerodha.getId(), TransactionType.BUY,
                new BigDecimal("10"), new BigDecimal("2500.00"), new BigDecimal("25"), LocalDate.of(2024, 3, 15), "Initial buy");
        transactionService.createTransaction(reliance.getId(), zerodha.getId(), TransactionType.BUY,
                new BigDecimal("5"), new BigDecimal("2700.00"), new BigDecimal("15"), LocalDate.of(2024, 6, 20), "Added more");

        transactionService.createTransaction(tcs.getId(), zerodha.getId(), TransactionType.BUY,
                new BigDecimal("8"), new BigDecimal("3600.00"), new BigDecimal("30"), LocalDate.of(2024, 2, 10), null);

        transactionService.createTransaction(infy.getId(), zerodha.getId(), TransactionType.BUY,
                new BigDecimal("20"), new BigDecimal("1450.00"), new BigDecimal("20"), LocalDate.of(2024, 1, 5), null);
        transactionService.createTransaction(infy.getId(), zerodha.getId(), TransactionType.SELL,
                new BigDecimal("5"), new BigDecimal("1550.00"), new BigDecimal("10"), LocalDate.of(2024, 8, 15), "Partial profit booking");

        transactionService.createTransaction(hdfcBank.getId(), zerodha.getId(), TransactionType.BUY,
                new BigDecimal("12"), new BigDecimal("1500.00"), new BigDecimal("18"), LocalDate.of(2024, 4, 1), null);

        transactionService.createTransaction(nifty50.getId(), groww.getId(), TransactionType.BUY,
                new BigDecimal("100"), new BigDecimal("220.00"), BigDecimal.ZERO, LocalDate.of(2024, 1, 10), "SIP");
        transactionService.createTransaction(nifty50.getId(), groww.getId(), TransactionType.BUY,
                new BigDecimal("100"), new BigDecimal("230.00"), BigDecimal.ZERO, LocalDate.of(2024, 4, 10), "SIP");

        transactionService.createTransaction(sensex.getId(), groww.getId(), TransactionType.BUY,
                new BigDecimal("50"), new BigDecimal("720.00"), BigDecimal.ZERO, LocalDate.of(2024, 2, 15), "SIP");

        transactionService.createTransaction(axisBlue.getId(), groww.getId(), TransactionType.BUY,
                new BigDecimal("200"), new BigDecimal("48.50"), BigDecimal.ZERO, LocalDate.of(2024, 3, 1), "Lump sum");

        transactionService.createTransaction(miraeAsset.getId(), groww.getId(), TransactionType.BUY,
                new BigDecimal("150"), new BigDecimal("88.00"), BigDecimal.ZERO, LocalDate.of(2024, 5, 1), "SIP");

        transactionService.createTransaction(btc.getId(), wazirx.getId(), TransactionType.BUY,
                new BigDecimal("0.01"), new BigDecimal("4500000"), new BigDecimal("450"), LocalDate.of(2024, 2, 20), null);

        transactionService.createTransaction(eth.getId(), wazirx.getId(), TransactionType.BUY,
                new BigDecimal("0.5"), new BigDecimal("200000"), new BigDecimal("100"), LocalDate.of(2024, 3, 10), null);

        transactionService.createTransaction(sbiFD.getId(), sbi.getId(), TransactionType.BUY,
                new BigDecimal("500000"), BigDecimal.ONE, BigDecimal.ZERO, LocalDate.of(2024, 1, 1), "1 year FD at 7.1%");

        transactionService.createTransaction(hdfcSavings.getId(), hdfc.getId(), TransactionType.BUY,
                new BigDecimal("200000"), BigDecimal.ONE, BigDecimal.ZERO, LocalDate.of(2024, 1, 1), "Emergency fund");

        System.out.println("Sample data loaded successfully!");
    }
}
