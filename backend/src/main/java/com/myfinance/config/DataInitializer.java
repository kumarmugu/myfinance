package com.myfinance.config;

import com.myfinance.model.*;
import com.myfinance.model.enums.*;
import com.myfinance.repository.*;
import com.myfinance.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final OwnerRepository ownerRepository;
    private final AccountRepository accountRepository;
    private final AssetRepository assetRepository;
    private final BankRepository bankRepository;
    private final FDHolderRepository fdHolderRepository;
    private final CurrencyRateRepository currencyRateRepository;
    private final AllocationTargetRepository allocationTargetRepository;
    private final FixedDepositRepository fixedDepositRepository;
    private final DividendRepository dividendRepository;
    private final SoldPositionRepository soldPositionRepository;
    private final AccountDepositRepository accountDepositRepository;
    private final NetWorthSnapshotRepository netWorthSnapshotRepository;
    private final TransactionService transactionService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (ownerRepository.count() > 0) return;

        // ─── Default Admin User ───
        appUserRepository.save(AppUser.builder()
                .username("admin")
                .email("admin@myfinance.local")
                .password(passwordEncoder.encode("admin123"))
                .displayName("Admin")
                .build());

        // ═══════════════════════════════════════════════════════
        // OWNERS
        // ═══════════════════════════════════════════════════════
        Owner self = ownerRepository.save(Owner.builder().name("Primary User").relationship(OwnerRelationship.SELF).build());
        Owner spouse = ownerRepository.save(Owner.builder().name("Spouse").relationship(OwnerRelationship.SPOUSE).build());

        // ═══════════════════════════════════════════════════════
        // ACCOUNTS
        // ═══════════════════════════════════════════════════════
        Account tiger = accountRepository.save(Account.builder().name("Tiger").accountType(AccountType.BROKER).owner(self).currency(Currency.USD).description("Tiger Brokers - US stocks").build());
        Account saxo = accountRepository.save(Account.builder().name("Saxo").accountType(AccountType.BROKER).owner(self).currency(Currency.SGD).description("Saxo Capital Markets").build());
        Account ibkr = accountRepository.save(Account.builder().name("IBKR").accountType(AccountType.BROKER).owner(self).currency(Currency.SGD).description("Interactive Brokers").build());
        Account poems = accountRepository.save(Account.builder().name("Poems").accountType(AccountType.BROKER).owner(self).currency(Currency.SGD).description("Phillip Securities - SRS & Cash").build());
        Account moomoo = accountRepository.save(Account.builder().name("Moomoo").accountType(AccountType.BROKER).owner(self).currency(Currency.SGD).description("Moomoo SG").build());
        Account dbs = accountRepository.save(Account.builder().name("DBS").accountType(AccountType.BANK).owner(self).currency(Currency.SGD).description("DBS Savings").build());
        Account ocbc = accountRepository.save(Account.builder().name("OCBC").accountType(AccountType.BANK).owner(self).currency(Currency.SGD).description("OCBC Savings").build());
        Account cimb = accountRepository.save(Account.builder().name("CIMB").accountType(AccountType.BANK).owner(self).currency(Currency.SGD).description("CIMB Savings").build());
        Account coinhako = accountRepository.save(Account.builder().name("Coinhako").accountType(AccountType.CRYPTO_EXCHANGE).owner(self).currency(Currency.SGD).description("Coinhako crypto exchange").build());
        Account cryptoCom = accountRepository.save(Account.builder().name("Crypto.com").accountType(AccountType.CRYPTO_EXCHANGE).owner(self).currency(Currency.SGD).description("Crypto.com wallet").build());
        accountRepository.save(Account.builder().name("SL-Fixed").accountType(AccountType.BANK).owner(self).currency(Currency.LKR).description("Sri Lanka Fixed Deposits").build());

        Account tigerSpouse = accountRepository.save(Account.builder().name("Tiger-Spouse").accountType(AccountType.BROKER).owner(spouse).currency(Currency.USD).description("Tiger Brokers - Spouse").build());
        Account saxoSpouse = accountRepository.save(Account.builder().name("Saxo-Spouse").accountType(AccountType.BROKER).owner(spouse).currency(Currency.SGD).description("Saxo - Spouse").build());

        // ═══════════════════════════════════════════════════════
        // ASSETS
        // ═══════════════════════════════════════════════════════
        // Index Funds
        Asset voo = assetRepository.save(Asset.builder().name("Vanguard S&P 500 ETF").symbol("VOO").assetType(AssetType.INDEX_FUND).currentPrice(new BigDecimal("530")).currency(Currency.USD).exchange("NYSE").build());
        Asset vgt = assetRepository.save(Asset.builder().name("Vanguard Info Tech ETF").symbol("VGT").assetType(AssetType.INDEX_FUND).currentPrice(new BigDecimal("580")).currency(Currency.USD).exchange("NYSE").build());
        Asset qqq = assetRepository.save(Asset.builder().name("Invesco QQQ Trust").symbol("QQQ").assetType(AssetType.INDEX_FUND).currentPrice(new BigDecimal("510")).currency(Currency.USD).exchange("NASDAQ").build());
        Asset vug = assetRepository.save(Asset.builder().name("Vanguard Growth ETF").symbol("VUG").assetType(AssetType.INDEX_FUND).currentPrice(new BigDecimal("390")).currency(Currency.USD).exchange("NYSE").build());
        Asset qqqm = assetRepository.save(Asset.builder().name("Invesco Nasdaq 100 ETF").symbol("QQQM").assetType(AssetType.INDEX_FUND).currentPrice(new BigDecimal("215")).currency(Currency.USD).exchange("NASDAQ").build());
        Asset spyl = assetRepository.save(Asset.builder().name("SPDR S&P 500 UCITS ETF").symbol("SPYL").assetType(AssetType.INDEX_FUND).currentPrice(new BigDecimal("18.50")).currency(Currency.USD).exchange("LSE").build());

        // Mutual Funds
        Asset amundiWorld = assetRepository.save(Asset.builder().name("Amundi Index MSCI World").symbol("AMUNDI-WORLD").assetType(AssetType.MUTUAL_FUND).currentPrice(new BigDecimal("215")).currency(Currency.SGD).exchange("Poems").build());
        Asset amundiUSA = assetRepository.save(Asset.builder().name("Amundi Prime USA").symbol("AMUNDI-USA").assetType(AssetType.MUTUAL_FUND).currentPrice(new BigDecimal("220")).currency(Currency.SGD).exchange("Poems").build());
        Asset fidelity = assetRepository.save(Asset.builder().name("Fidelity Global Technology").symbol("FIDELITY-TECH").assetType(AssetType.MUTUAL_FUND).currentPrice(new BigDecimal("75")).currency(Currency.USD).exchange("Poems").build());

        // Growth Equities
        Asset tsla = assetRepository.save(Asset.builder().name("Tesla Inc").symbol("TSLA").assetType(AssetType.GROWTH_EQUITY).currentPrice(new BigDecimal("350")).currency(Currency.USD).exchange("NASDAQ").build());
        Asset aapl = assetRepository.save(Asset.builder().name("Apple Inc").symbol("AAPL").assetType(AssetType.GROWTH_EQUITY).currentPrice(new BigDecimal("230")).currency(Currency.USD).exchange("NASDAQ").build());
        Asset nvda = assetRepository.save(Asset.builder().name("NVIDIA Corp").symbol("NVDA").assetType(AssetType.GROWTH_EQUITY).currentPrice(new BigDecimal("140")).currency(Currency.USD).exchange("NASDAQ").build());
        Asset meta = assetRepository.save(Asset.builder().name("Meta Platforms").symbol("META").assetType(AssetType.GROWTH_EQUITY).currentPrice(new BigDecimal("580")).currency(Currency.USD).exchange("NASDAQ").build());
        Asset googl = assetRepository.save(Asset.builder().name("Alphabet Inc").symbol("GOOGL").assetType(AssetType.GROWTH_EQUITY).currentPrice(new BigDecimal("175")).currency(Currency.USD).exchange("NASDAQ").build());
        Asset amzn = assetRepository.save(Asset.builder().name("Amazon.com").symbol("AMZN").assetType(AssetType.GROWTH_EQUITY).currentPrice(new BigDecimal("195")).currency(Currency.USD).exchange("NASDAQ").build());
        Asset pltr = assetRepository.save(Asset.builder().name("Palantir Technologies").symbol("PLTR").assetType(AssetType.GROWTH_EQUITY).currentPrice(new BigDecimal("72")).currency(Currency.USD).exchange("NYSE").build());
        Asset msft = assetRepository.save(Asset.builder().name("Microsoft Corp").symbol("MSFT").assetType(AssetType.GROWTH_EQUITY).currentPrice(new BigDecimal("440")).currency(Currency.USD).exchange("NASDAQ").build());
        Asset crwd = assetRepository.save(Asset.builder().name("CrowdStrike Holdings").symbol("CRWD").assetType(AssetType.GROWTH_EQUITY).currentPrice(new BigDecimal("360")).currency(Currency.USD).exchange("NASDAQ").build());

        // Dividend Equities
        Asset dbsSg = assetRepository.save(Asset.builder().name("DBS Group Holdings").symbol("D05").assetType(AssetType.DIVIDEND_EQUITY).currentPrice(new BigDecimal("42")).currency(Currency.SGD).exchange("SGX").build());
        Asset ocbcSg = assetRepository.save(Asset.builder().name("OCBC Bank").symbol("O39").assetType(AssetType.DIVIDEND_EQUITY).currentPrice(new BigDecimal("16")).currency(Currency.SGD).exchange("SGX").build());
        Asset singtel = assetRepository.save(Asset.builder().name("Singapore Telecommunications").symbol("Z74").assetType(AssetType.DIVIDEND_EQUITY).currentPrice(new BigDecimal("3.80")).currency(Currency.SGD).exchange("SGX").build());

        // Leveraged ETFs
        Asset tqqq = assetRepository.save(Asset.builder().name("ProShares UltraPro QQQ").symbol("TQQQ").assetType(AssetType.LEVERAGED_ETF).currentPrice(new BigDecimal("62")).currency(Currency.USD).exchange("NASDAQ").build());
        Asset spxl = assetRepository.save(Asset.builder().name("Direxion Daily S&P 500 Bull 3X").symbol("SPXL").assetType(AssetType.LEVERAGED_ETF).currentPrice(new BigDecimal("175")).currency(Currency.USD).exchange("NYSE").build());

        // Crypto
        Asset btc = assetRepository.save(Asset.builder().name("Bitcoin").symbol("BTC").assetType(AssetType.CRYPTO).currentPrice(new BigDecimal("95000")).currency(Currency.USD).exchange("Coinhako").build());
        Asset eth = assetRepository.save(Asset.builder().name("Ethereum").symbol("ETH").assetType(AssetType.CRYPTO).currentPrice(new BigDecimal("3200")).currency(Currency.USD).exchange("Crypto.com").build());
        Asset sol = assetRepository.save(Asset.builder().name("Solana").symbol("SOL").assetType(AssetType.CRYPTO).currentPrice(new BigDecimal("180")).currency(Currency.USD).exchange("Coinhako").build());

        // ═══════════════════════════════════════════════════════
        // TRANSACTIONS (creates holdings automatically)
        // ═══════════════════════════════════════════════════════
        // Tiger - Index
        transactionService.create(voo.getId(), tiger.getId(), self.getId(), TransactionType.BUY, new BigDecimal("10"), new BigDecimal("390"), BigDecimal.ZERO, "USD", LocalDate.of(2022, 3, 15), "Initial VOO buy");
        transactionService.create(voo.getId(), tiger.getId(), self.getId(), TransactionType.BUY, new BigDecimal("5"), new BigDecimal("460"), BigDecimal.ZERO, "USD", LocalDate.of(2024, 3, 20), "Added more VOO");
        transactionService.create(qqqm.getId(), tiger.getId(), self.getId(), TransactionType.BUY, new BigDecimal("12"), new BigDecimal("175"), BigDecimal.ZERO, "USD", LocalDate.of(2024, 8, 5), "QQQM position");

        // Tiger - Growth
        transactionService.create(tsla.getId(), tiger.getId(), self.getId(), TransactionType.BUY, new BigDecimal("20"), new BigDecimal("220"), BigDecimal.ZERO, "USD", LocalDate.of(2022, 5, 12), null);
        transactionService.create(tsla.getId(), tiger.getId(), self.getId(), TransactionType.BUY, new BigDecimal("10"), new BigDecimal("165"), BigDecimal.ZERO, "USD", LocalDate.of(2024, 4, 16), null);
        transactionService.create(aapl.getId(), tiger.getId(), self.getId(), TransactionType.BUY, new BigDecimal("14"), new BigDecimal("170"), BigDecimal.ZERO, "USD", LocalDate.of(2024, 3, 22), null);
        transactionService.create(nvda.getId(), tiger.getId(), self.getId(), TransactionType.BUY, new BigDecimal("30"), new BigDecimal("85"), BigDecimal.ZERO, "USD", LocalDate.of(2024, 4, 9), null);
        transactionService.create(pltr.getId(), tiger.getId(), self.getId(), TransactionType.BUY, new BigDecimal("45"), new BigDecimal("11"), BigDecimal.ZERO, "USD", LocalDate.of(2022, 5, 9), "Early buy");
        transactionService.create(crwd.getId(), tiger.getId(), self.getId(), TransactionType.BUY, new BigDecimal("10"), new BigDecimal("227"), BigDecimal.ZERO, "USD", LocalDate.of(2024, 8, 1), null);

        // Tiger - Leveraged
        transactionService.create(tqqq.getId(), tiger.getId(), self.getId(), TransactionType.BUY, new BigDecimal("50"), new BigDecimal("28"), BigDecimal.ZERO, "USD", LocalDate.of(2022, 5, 9), null);
        transactionService.create(spxl.getId(), tiger.getId(), self.getId(), TransactionType.BUY, new BigDecimal("20"), new BigDecimal("85"), BigDecimal.ZERO, "USD", LocalDate.of(2022, 5, 21), null);

        // Saxo - Index
        transactionService.create(voo.getId(), saxo.getId(), self.getId(), TransactionType.BUY, new BigDecimal("37"), new BigDecimal("550"), BigDecimal.ZERO, "SGD", LocalDate.of(2021, 8, 18), "Saxo VOO");
        transactionService.create(vgt.getId(), saxo.getId(), self.getId(), TransactionType.BUY, new BigDecimal("42"), new BigDecimal("550"), BigDecimal.ZERO, "SGD", LocalDate.of(2021, 9, 2), null);
        transactionService.create(qqq.getId(), saxo.getId(), self.getId(), TransactionType.BUY, new BigDecimal("49"), new BigDecimal("510"), BigDecimal.ZERO, "SGD", LocalDate.of(2021, 8, 3), null);
        transactionService.create(vug.getId(), saxo.getId(), self.getId(), TransactionType.BUY, new BigDecimal("20"), new BigDecimal("405"), BigDecimal.ZERO, "SGD", LocalDate.of(2021, 8, 11), null);

        // Saxo - Growth
        transactionService.create(tsla.getId(), saxo.getId(), self.getId(), TransactionType.BUY, new BigDecimal("21"), new BigDecimal("420"), BigDecimal.ZERO, "SGD", LocalDate.of(2021, 12, 3), null);
        transactionService.create(meta.getId(), saxo.getId(), self.getId(), TransactionType.BUY, new BigDecimal("3"), new BigDecimal("310"), BigDecimal.ZERO, "SGD", LocalDate.of(2022, 2, 7), null);
        transactionService.create(msft.getId(), saxo.getId(), self.getId(), TransactionType.BUY, new BigDecimal("7"), new BigDecimal("435"), BigDecimal.ZERO, "SGD", LocalDate.of(2021, 12, 21), null);

        // IBKR - Index
        transactionService.create(spyl.getId(), ibkr.getId(), self.getId(), TransactionType.BUY, new BigDecimal("500"), new BigDecimal("15"), BigDecimal.ZERO, "USD", LocalDate.of(2025, 2, 10), null);
        transactionService.create(spyl.getId(), ibkr.getId(), self.getId(), TransactionType.BUY, new BigDecimal("500"), new BigDecimal("16"), BigDecimal.ZERO, "USD", LocalDate.of(2025, 5, 12), null);
        transactionService.create(voo.getId(), ibkr.getId(), self.getId(), TransactionType.BUY, new BigDecimal("10"), new BigDecimal("500"), BigDecimal.ZERO, "USD", LocalDate.of(2025, 4, 3), null);

        // IBKR - Growth
        transactionService.create(googl.getId(), ibkr.getId(), self.getId(), TransactionType.BUY, new BigDecimal("48"), new BigDecimal("165"), BigDecimal.ZERO, "USD", LocalDate.of(2025, 2, 6), null);
        transactionService.create(amzn.getId(), ibkr.getId(), self.getId(), TransactionType.BUY, new BigDecimal("44"), new BigDecimal("185"), BigDecimal.ZERO, "USD", LocalDate.of(2025, 3, 10), null);
        transactionService.create(aapl.getId(), ibkr.getId(), self.getId(), TransactionType.BUY, new BigDecimal("20"), new BigDecimal("195"), BigDecimal.ZERO, "USD", LocalDate.of(2025, 4, 4), null);
        transactionService.create(nvda.getId(), ibkr.getId(), self.getId(), TransactionType.BUY, new BigDecimal("25"), new BigDecimal("100"), BigDecimal.ZERO, "USD", LocalDate.of(2025, 4, 4), null);

        // IBKR - Dividend
        transactionService.create(dbsSg.getId(), ibkr.getId(), self.getId(), TransactionType.BUY, new BigDecimal("100"), new BigDecimal("36"), BigDecimal.ZERO, "SGD", LocalDate.of(2024, 7, 22), null);
        transactionService.create(ocbcSg.getId(), ibkr.getId(), self.getId(), TransactionType.BUY, new BigDecimal("100"), new BigDecimal("15"), BigDecimal.ZERO, "SGD", LocalDate.of(2024, 7, 15), null);
        transactionService.create(singtel.getId(), ibkr.getId(), self.getId(), TransactionType.BUY, new BigDecimal("900"), new BigDecimal("3.05"), BigDecimal.ZERO, "SGD", LocalDate.of(2025, 1, 9), null);

        // Poems - Mutual Funds
        transactionService.create(amundiWorld.getId(), poems.getId(), self.getId(), TransactionType.BUY, new BigDecimal("50"), new BigDecimal("192"), BigDecimal.ZERO, "SGD", LocalDate.of(2025, 7, 7), "SRS");
        transactionService.create(amundiUSA.getId(), poems.getId(), self.getId(), TransactionType.BUY, new BigDecimal("80"), new BigDecimal("200"), BigDecimal.ZERO, "SGD", LocalDate.of(2025, 7, 17), "SRS");
        transactionService.create(fidelity.getId(), poems.getId(), self.getId(), TransactionType.BUY, new BigDecimal("80"), new BigDecimal("62"), BigDecimal.ZERO, "USD", LocalDate.of(2021, 12, 1), null);

        // Crypto
        transactionService.create(btc.getId(), coinhako.getId(), self.getId(), TransactionType.BUY, new BigDecimal("0.05"), new BigDecimal("45000"), BigDecimal.ZERO, "USD", LocalDate.of(2021, 11, 15), null);
        transactionService.create(eth.getId(), cryptoCom.getId(), self.getId(), TransactionType.BUY, new BigDecimal("1.6"), new BigDecimal("1800"), BigDecimal.ZERO, "USD", LocalDate.of(2021, 12, 3), null);
        transactionService.create(sol.getId(), coinhako.getId(), self.getId(), TransactionType.BUY, new BigDecimal("1.2"), new BigDecimal("80"), BigDecimal.ZERO, "USD", LocalDate.of(2022, 9, 15), null);

        // Spouse - Tiger
        transactionService.create(voo.getId(), tigerSpouse.getId(), spouse.getId(), TransactionType.BUY, new BigDecimal("3"), new BigDecimal("386"), BigDecimal.ZERO, "USD", LocalDate.of(2022, 3, 8), null);
        transactionService.create(qqq.getId(), tigerSpouse.getId(), spouse.getId(), TransactionType.BUY, new BigDecimal("5"), new BigDecimal("310"), BigDecimal.ZERO, "USD", LocalDate.of(2022, 3, 8), null);
        transactionService.create(tsla.getId(), tigerSpouse.getId(), spouse.getId(), TransactionType.BUY, new BigDecimal("18"), new BigDecimal("250"), BigDecimal.ZERO, "USD", LocalDate.of(2022, 5, 11), null);

        // Spouse - Saxo
        transactionService.create(voo.getId(), saxoSpouse.getId(), spouse.getId(), TransactionType.BUY, new BigDecimal("8"), new BigDecimal("564"), BigDecimal.ZERO, "SGD", LocalDate.of(2021, 9, 1), null);

        // ═══════════════════════════════════════════════════════
        // SOLD POSITIONS
        // ═══════════════════════════════════════════════════════
        soldPositionRepository.save(SoldPosition.builder().asset(nvda).account(tiger).owner(self).quantity(new BigDecimal("30")).buyPrice(new BigDecimal("95")).sellPrice(new BigDecimal("135")).investedAmount(new BigDecimal("2850")).soldAmount(new BigDecimal("4050")).profit(new BigDecimal("1200")).profitPercentage(new BigDecimal("42.1")).currency(Currency.USD).investedDate(LocalDate.of(2024, 3, 8)).soldDate(LocalDate.of(2024, 6, 19)).holdingPeriod("3 Months").isShortTerm(false).notes("Partial profit booking").build());
        soldPositionRepository.save(SoldPosition.builder().asset(tsla).account(tiger).owner(self).quantity(new BigDecimal("16")).buyPrice(new BigDecimal("411")).sellPrice(new BigDecimal("450")).investedAmount(new BigDecimal("6576")).soldAmount(new BigDecimal("7200")).profit(new BigDecimal("624")).profitPercentage(new BigDecimal("9.5")).currency(Currency.USD).investedDate(LocalDate.of(2024, 12, 31)).soldDate(LocalDate.of(2025, 9, 29)).holdingPeriod("9 Months").isShortTerm(false).build());
        soldPositionRepository.save(SoldPosition.builder().asset(spxl).account(tiger).owner(self).quantity(new BigDecimal("20")).buyPrice(new BigDecimal("125")).sellPrice(new BigDecimal("156")).investedAmount(new BigDecimal("2500")).soldAmount(new BigDecimal("3120")).profit(new BigDecimal("620")).profitPercentage(new BigDecimal("24.8")).currency(Currency.USD).investedDate(LocalDate.of(2024, 4, 5)).soldDate(LocalDate.of(2025, 6, 23)).holdingPeriod("1Y 2M").isShortTerm(false).build());

        // Short-term trades
        soldPositionRepository.save(SoldPosition.builder().asset(tqqq).account(tiger).owner(self).quantity(new BigDecimal("30")).buyPrice(new BigDecimal("100")).sellPrice(new BigDecimal("117")).investedAmount(new BigDecimal("3000")).soldAmount(new BigDecimal("3510")).profit(new BigDecimal("510")).profitPercentage(new BigDecimal("17")).currency(Currency.USD).investedDate(LocalDate.of(2025, 10, 11)).soldDate(LocalDate.of(2025, 10, 28)).holdingPeriod("17 days").isShortTerm(true).build());
        soldPositionRepository.save(SoldPosition.builder().asset(tqqq).account(tiger).owner(self).quantity(new BigDecimal("50")).buyPrice(new BigDecimal("58")).sellPrice(new BigDecimal("73")).investedAmount(new BigDecimal("2900")).soldAmount(new BigDecimal("3650")).profit(new BigDecimal("750")).profitPercentage(new BigDecimal("25.9")).currency(Currency.USD).investedDate(LocalDate.of(2024, 4, 15)).soldDate(LocalDate.of(2025, 6, 23)).holdingPeriod("1Y 2M").isShortTerm(true).build());

        // ═══════════════════════════════════════════════════════
        // DIVIDENDS
        // ═══════════════════════════════════════════════════════
        // Saxo dividends
        String[] saxoInstruments = {"QQQ", "VOO", "VGT", "VUG", "MSFT", "META", "TQQQ"};
        BigDecimal[] q1Amounts = {bd("32"), bd("63"), bd("29"), bd("9"), bd("5"), bd("1.5"), bd("2")};
        BigDecimal[] q2Amounts = {bd("26"), bd("58"), bd("26"), bd("9"), bd("5"), bd("1.4"), bd("2")};
        BigDecimal[] q3Amounts = {bd("31"), bd("58"), bd("33"), bd("9"), bd("5"), bd("1.4"), bd("4")};
        BigDecimal[] q4Amounts = {bd("35"), bd("59"), bd("29"), bd("9"), bd("6"), bd("1.4"), bd("1.5")};

        for (int i = 0; i < saxoInstruments.length; i++) {
            dividendRepository.save(Dividend.builder().account(saxo).owner(self).amount(q1Amounts[i]).currency(Currency.SGD).receivedDate(LocalDate.of(2025, 3, 28)).year(2025).quarter("Q1").instrument(saxoInstruments[i]).build());
            dividendRepository.save(Dividend.builder().account(saxo).owner(self).amount(q2Amounts[i]).currency(Currency.SGD).receivedDate(LocalDate.of(2025, 6, 28)).year(2025).quarter("Q2").instrument(saxoInstruments[i]).build());
            dividendRepository.save(Dividend.builder().account(saxo).owner(self).amount(q3Amounts[i]).currency(Currency.SGD).receivedDate(LocalDate.of(2025, 9, 28)).year(2025).quarter("Q3").instrument(saxoInstruments[i]).build());
            dividendRepository.save(Dividend.builder().account(saxo).owner(self).amount(q4Amounts[i]).currency(Currency.SGD).receivedDate(LocalDate.of(2025, 12, 28)).year(2025).quarter("Q4").instrument(saxoInstruments[i]).build());
        }

        // IBKR dividends
        dividendRepository.save(Dividend.builder().account(ibkr).owner(self).amount(bd("54")).currency(Currency.SGD).receivedDate(LocalDate.of(2025, 8, 15)).year(2025).quarter("Q3").instrument("D05").build());
        dividendRepository.save(Dividend.builder().account(ibkr).owner(self).amount(bd("44")).currency(Currency.SGD).receivedDate(LocalDate.of(2025, 8, 15)).year(2025).quarter("Q3").instrument("O39").build());
        dividendRepository.save(Dividend.builder().account(ibkr).owner(self).amount(bd("120")).currency(Currency.SGD).receivedDate(LocalDate.of(2025, 5, 20)).year(2025).quarter("Q2").instrument("Z74").build());

        // Tiger dividends
        dividendRepository.save(Dividend.builder().account(tiger).owner(self).amount(bd("85")).currency(Currency.USD).receivedDate(LocalDate.of(2025, 6, 15)).year(2025).quarter("Q2").instrument("VOO").build());
        dividendRepository.save(Dividend.builder().account(tiger).owner(self).amount(bd("45")).currency(Currency.USD).receivedDate(LocalDate.of(2025, 9, 15)).year(2025).quarter("Q3").instrument("AAPL").build());

        // ═══════════════════════════════════════════════════════
        // FIXED DEPOSITS (Sri Lanka)
        // ═══════════════════════════════════════════════════════
        Bank nsb = bankRepository.save(Bank.builder().name("National Savings Bank").shortName("NSB").build());
        Bank boc = bankRepository.save(Bank.builder().name("Bank of Ceylon").shortName("BOC").build());
        Bank commercial = bankRepository.save(Bank.builder().name("Commercial Bank").shortName("Commercial").build());
        Bank seylan = bankRepository.save(Bank.builder().name("Seylan Bank").shortName("Seylan").build());
        Bank peoples = bankRepository.save(Bank.builder().name("People's Bank").shortName("Peoples").build());
        Bank hnb = bankRepository.save(Bank.builder().name("Hatton National Bank").shortName("HNB").build());
        bankRepository.save(Bank.builder().name("Sampath Bank").shortName("Sampath").build());

        FDHolder parentA = fdHolderRepository.save(FDHolder.builder().name("Parent A").relationship("Mother").isSeniorCitizen(true).build());
        FDHolder parentB = fdHolderRepository.save(FDHolder.builder().name("Parent B").relationship("Father").isSeniorCitizen(true).build());
        FDHolder selfParentB = fdHolderRepository.save(FDHolder.builder().name("Self/Parent B").relationship("Self/Father").isSeniorCitizen(false).build());
        FDHolder selfParentA = fdHolderRepository.save(FDHolder.builder().name("Self/Parent A").relationship("Self/Mother").isSeniorCitizen(false).build());
        FDHolder sib1ParentB = fdHolderRepository.save(FDHolder.builder().name("Sibling1/Parent B").relationship("Sibling/Father").isSeniorCitizen(false).build());
        FDHolder sib1ParentA = fdHolderRepository.save(FDHolder.builder().name("Sibling1/Parent A").relationship("Sibling/Mother").isSeniorCitizen(false).build());
        fdHolderRepository.save(FDHolder.builder().name("Sibling 1").relationship("Sibling").isSeniorCitizen(false).build());
        fdHolderRepository.save(FDHolder.builder().name("Sibling2/Parent B").relationship("Sibling/Father").isSeniorCitizen(false).build());
        fdHolderRepository.save(FDHolder.builder().name("Sibling2/Parent A").relationship("Sibling/Mother").isSeniorCitizen(false).build());

        // Active FDs
        fixedDepositRepository.save(FixedDeposit.builder().holder(parentA).bank(nsb).accountNumber("FD-NSB-001").principalAmount(bd("500000")).interestRate(bd("8")).startDate(LocalDate.of(2025, 1, 11)).maturityDate(LocalDate.of(2026, 1, 11)).period("12 Months").branch("Main Branch").category("SENIOR_CITIZEN").status(FDStatus.ACTIVE).expectedInterest(bd("40000")).beneficiary("PARENT_A").build());
        fixedDepositRepository.save(FixedDeposit.builder().holder(parentB).bank(hnb).accountNumber("FD-HNB-001").principalAmount(bd("1000000")).interestRate(bd("8")).startDate(LocalDate.of(2025, 5, 22)).maturityDate(LocalDate.of(2026, 5, 22)).period("12 Months").branch("Main Branch").category("SENIOR_CITIZEN").status(FDStatus.ACTIVE).expectedInterest(bd("80000")).beneficiary("PARENT_B").build());
        fixedDepositRepository.save(FixedDeposit.builder().holder(parentA).bank(commercial).accountNumber("FD-COM-001").principalAmount(bd("200000")).interestRate(bd("8")).startDate(LocalDate.of(2025, 1, 28)).maturityDate(LocalDate.of(2026, 1, 28)).period("12 Months").branch("Main Branch").category("SENIOR_CITIZEN").status(FDStatus.ACTIVE).expectedInterest(bd("16000")).beneficiary("PARENT_A").build());
        fixedDepositRepository.save(FixedDeposit.builder().holder(selfParentB).bank(seylan).accountNumber("FD-SEY-001").principalAmount(bd("750000")).interestRate(bd("7.5")).startDate(LocalDate.of(2025, 4, 21)).maturityDate(LocalDate.of(2026, 4, 21)).period("12 Months").branch("Main Branch").status(FDStatus.ACTIVE).expectedInterest(bd("56250")).beneficiary("PARENT_B").build());
        fixedDepositRepository.save(FixedDeposit.builder().holder(selfParentB).bank(boc).accountNumber("FD-BOC-001").principalAmount(bd("700000")).interestRate(bd("7.5")).startDate(LocalDate.of(2025, 4, 19)).maturityDate(LocalDate.of(2026, 4, 19)).period("12 Months").branch("Main Branch").status(FDStatus.ACTIVE).expectedInterest(bd("52500")).beneficiary("PARENT_B").build());
        fixedDepositRepository.save(FixedDeposit.builder().holder(selfParentA).bank(commercial).accountNumber("FD-COM-002").principalAmount(bd("1000000")).interestRate(bd("11.46")).startDate(LocalDate.of(2025, 9, 8)).maturityDate(LocalDate.of(2026, 9, 8)).period("12 Months").branch("Main Branch").category("SENIOR_CITIZEN").status(FDStatus.ACTIVE).expectedInterest(bd("114600")).beneficiary("PARENT_A").build());
        fixedDepositRepository.save(FixedDeposit.builder().holder(selfParentB).bank(boc).accountNumber("FD-BOC-002").principalAmount(bd("2400000")).interestRate(bd("10")).startDate(LocalDate.of(2026, 6, 11)).maturityDate(LocalDate.of(2027, 7, 15)).period("400 days").branch("Main Branch").status(FDStatus.ACTIVE).expectedInterest(bd("240000")).beneficiary("PARENT_B").purpose("deed").build());
        fixedDepositRepository.save(FixedDeposit.builder().holder(sib1ParentB).bank(boc).accountNumber("FD-BOC-003").principalAmount(bd("2500000")).interestRate(bd("10")).startDate(LocalDate.of(2026, 6, 19)).maturityDate(LocalDate.of(2027, 7, 15)).period("400 days").branch("Main Branch").status(FDStatus.ACTIVE).expectedInterest(bd("250000")).beneficiary("PARENT_B").build());
        fixedDepositRepository.save(FixedDeposit.builder().holder(sib1ParentA).bank(boc).accountNumber("FD-BOC-004").principalAmount(bd("1200000")).interestRate(bd("7.5")).startDate(LocalDate.of(2025, 4, 21)).maturityDate(LocalDate.of(2026, 4, 21)).period("12 Months").branch("Main Branch").status(FDStatus.ACTIVE).expectedInterest(bd("90000")).beneficiary("PARENT_A").build());
        fixedDepositRepository.save(FixedDeposit.builder().holder(parentB).bank(boc).accountNumber("FD-BOC-005").principalAmount(bd("900000")).interestRate(bd("10")).startDate(LocalDate.of(2026, 6, 11)).maturityDate(LocalDate.of(2027, 7, 15)).period("400 days").branch("Main Branch").status(FDStatus.ACTIVE).expectedInterest(bd("90000")).beneficiary("PARENT_B").build());

        // FDs needing renewal
        fixedDepositRepository.save(FixedDeposit.builder().holder(sib1ParentA).bank(nsb).accountNumber("FD-NSB-002").principalAmount(bd("780000")).interestRate(bd("6.85")).startDate(LocalDate.of(2025, 1, 15)).maturityDate(LocalDate.of(2025, 7, 15)).period("7 Months").branch("Main Branch").status(FDStatus.ACTIVE).expectedInterest(bd("53000")).beneficiary("PARENT_A").requiresUpdate(true).notes("needs switch to 1 yr").build());
        fixedDepositRepository.save(FixedDeposit.builder().holder(parentA).bank(peoples).accountNumber("FD-PB-001").principalAmount(bd("460000")).interestRate(bd("6.5")).startDate(LocalDate.of(2023, 2, 3)).maturityDate(LocalDate.of(2024, 2, 3)).period("12 Months").branch("Main Branch").status(FDStatus.REQUIRES_UPDATE).expectedInterest(bd("29900")).beneficiary("PARENT_A").requiresUpdate(true).notes("Old certificate - verify with bank").build());

        // Maturing soon (within 30 days from now)
        fixedDepositRepository.save(FixedDeposit.builder().holder(sib1ParentA).bank(boc).accountNumber("FD-BOC-006").principalAmount(bd("850000")).interestRate(bd("7")).startDate(LocalDate.of(2025, 6, 20)).maturityDate(LocalDate.now().plusDays(15)).period("200 days").branch("Main Branch").status(FDStatus.ACTIVE).expectedInterest(bd("59000")).beneficiary("PARENT_A").notes("Maturing soon").build());
        fixedDepositRepository.save(FixedDeposit.builder().holder(selfParentA).bank(boc).accountNumber("FD-BOC-007").principalAmount(bd("900000")).interestRate(bd("8.5")).startDate(LocalDate.of(2024, 9, 4)).maturityDate(LocalDate.now().plusDays(25)).period("12 Months").branch("Main Branch").status(FDStatus.ACTIVE).expectedInterest(bd("76500")).beneficiary("PARENT_A").build());

        // ═══════════════════════════════════════════════════════
        // ACCOUNT DEPOSITS/WITHDRAWALS
        // ═══════════════════════════════════════════════════════
        accountDepositRepository.save(AccountDeposit.builder().account(tiger).amount(bd("5000")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2022, 2, 23)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(tiger).amount(bd("10000")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2022, 5, 11)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(tiger).amount(bd("40000")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2024, 3, 23)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(tiger).amount(bd("50000")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2025, 5, 8)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(saxo).amount(bd("7500")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2021, 7, 31)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(saxo).amount(bd("10000")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2021, 8, 18)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(saxo).amount(bd("10000")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2021, 9, 13)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(ibkr).amount(bd("10000")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2025, 1, 13)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(ibkr).amount(bd("5000")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2025, 2, 5)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(ibkr).amount(bd("20000")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2025, 3, 13)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(ibkr).amount(bd("20000")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2025, 4, 3)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(poems).amount(bd("10000")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2025, 7, 7)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(poems).amount(bd("10000")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2025, 7, 17)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(moomoo).amount(bd("25000")).depositType("DEPOSIT").currency(Currency.SGD).depositDate(LocalDate.of(2024, 5, 2)).build());
        accountDepositRepository.save(AccountDeposit.builder().account(moomoo).amount(bd("20000")).depositType("WITHDRAWAL").currency(Currency.SGD).depositDate(LocalDate.of(2025, 3, 12)).build());

        // ═══════════════════════════════════════════════════════
        // NET WORTH SNAPSHOTS (historical)
        // ═══════════════════════════════════════════════════════
        netWorthSnapshotRepository.save(NetWorthSnapshot.builder().snapshotDate(LocalDate.of(2022, 12, 31)).year(2022).totalIndexFund(bd("45000")).totalMutualFund(bd("5000")).totalGrowthEquity(bd("35000")).totalDividendEquity(bd("0")).totalLeveragedEtf(bd("5000")).totalMoneyMarket(bd("0")).totalFixedDeposit(bd("27000")).totalSavings(bd("135000")).totalCrypto(bd("5500")).totalNetWorth(bd("257500")).build());
        netWorthSnapshotRepository.save(NetWorthSnapshot.builder().snapshotDate(LocalDate.of(2023, 12, 31)).year(2023).totalIndexFund(bd("85000")).totalMutualFund(bd("9000")).totalGrowthEquity(bd("60000")).totalDividendEquity(bd("0")).totalLeveragedEtf(bd("12000")).totalMoneyMarket(bd("55000")).totalFixedDeposit(bd("24000")).totalSavings(bd("180000")).totalCrypto(bd("10500")).totalNetWorth(bd("435500")).build());
        netWorthSnapshotRepository.save(NetWorthSnapshot.builder().snapshotDate(LocalDate.of(2024, 12, 31)).year(2024).totalIndexFund(bd("180000")).totalMutualFund(bd("11000")).totalGrowthEquity(bd("130000")).totalDividendEquity(bd("12000")).totalLeveragedEtf(bd("22000")).totalMoneyMarket(bd("65000")).totalFixedDeposit(bd("33000")).totalSavings(bd("195000")).totalCrypto(bd("17500")).totalNetWorth(bd("665500")).build());
        netWorthSnapshotRepository.save(NetWorthSnapshot.builder().snapshotDate(LocalDate.of(2025, 6, 30)).year(2025).totalIndexFund(bd("250000")).totalMutualFund(bd("15000")).totalGrowthEquity(bd("180000")).totalDividendEquity(bd("20000")).totalLeveragedEtf(bd("15000")).totalMoneyMarket(bd("60000")).totalFixedDeposit(bd("20000")).totalSavings(bd("150000")).totalCrypto(bd("12000")).totalNetWorth(bd("722000")).build());

        // ═══════════════════════════════════════════════════════
        // ALLOCATION TARGETS
        // ═══════════════════════════════════════════════════════
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.INDEX_FUND).targetPercentage(bd("37")).targetAmount(bd("370000")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.GROWTH_EQUITY).targetPercentage(bd("23")).targetAmount(bd("230000")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.DIVIDEND_EQUITY).targetPercentage(bd("10")).targetAmount(bd("100000")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.MONEY_MARKET).targetPercentage(bd("10")).targetAmount(bd("100000")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.LEVERAGED_ETF).targetPercentage(bd("5")).targetAmount(bd("50000")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.FIXED_DEPOSIT).targetPercentage(bd("5")).targetAmount(bd("50000")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.CRYPTO).targetPercentage(bd("5")).targetAmount(bd("50000")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.SAVINGS).targetPercentage(bd("3")).targetAmount(bd("30000")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.MUTUAL_FUND).targetPercentage(bd("2")).targetAmount(bd("20000")).build());

        // ═══════════════════════════════════════════════════════
        // CURRENCY RATES
        // ═══════════════════════════════════════════════════════
        currencyRateRepository.save(CurrencyRate.builder().fromCurrency(Currency.USD).toCurrency(Currency.SGD).rate(bd("1.35")).effectiveDate(LocalDate.now()).build());
        currencyRateRepository.save(CurrencyRate.builder().fromCurrency(Currency.EUR).toCurrency(Currency.SGD).rate(bd("1.45")).effectiveDate(LocalDate.now()).build());
        currencyRateRepository.save(CurrencyRate.builder().fromCurrency(Currency.LKR).toCurrency(Currency.SGD).rate(bd("0.004")).effectiveDate(LocalDate.now()).build());

        System.out.println("Sample data initialized successfully!");
    }

    private BigDecimal bd(String val) { return new BigDecimal(val); }
}
