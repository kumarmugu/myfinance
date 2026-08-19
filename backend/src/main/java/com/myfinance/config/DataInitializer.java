package com.myfinance.config;

import com.myfinance.model.*;
import com.myfinance.model.enums.*;
import com.myfinance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final OwnerRepository ownerRepository;
    private final AccountRepository accountRepository;
    private final BankRepository bankRepository;
    private final FDHolderRepository fdHolderRepository;
    private final CurrencyRateRepository currencyRateRepository;
    private final AllocationTargetRepository allocationTargetRepository;

    @Override
    public void run(String... args) {
        if (ownerRepository.count() > 0) return;

        // ─── Owners ───
        Owner self = ownerRepository.save(Owner.builder().name("Primary User").relationship(OwnerRelationship.SELF).build());
        Owner spouse = ownerRepository.save(Owner.builder().name("Spouse").relationship(OwnerRelationship.SPOUSE).build());

        // ─── Accounts (Brokers & Banks) ───
        accountRepository.save(Account.builder().name("Tiger").accountType(AccountType.BROKER).owner(self).currency(Currency.USD).description("Tiger Brokers - US stocks").build());
        accountRepository.save(Account.builder().name("Saxo").accountType(AccountType.BROKER).owner(self).currency(Currency.SGD).description("Saxo Capital Markets").build());
        accountRepository.save(Account.builder().name("IBKR").accountType(AccountType.BROKER).owner(self).currency(Currency.SGD).description("Interactive Brokers").build());
        accountRepository.save(Account.builder().name("Poems").accountType(AccountType.BROKER).owner(self).currency(Currency.SGD).description("Phillip Securities - SRS & Cash").build());
        accountRepository.save(Account.builder().name("Moomoo").accountType(AccountType.BROKER).owner(self).currency(Currency.SGD).description("Moomoo SG").build());
        accountRepository.save(Account.builder().name("DBS").accountType(AccountType.BANK).owner(self).currency(Currency.SGD).description("DBS Savings").build());
        accountRepository.save(Account.builder().name("OCBC").accountType(AccountType.BANK).owner(self).currency(Currency.SGD).description("OCBC Savings").build());
        accountRepository.save(Account.builder().name("CIMB").accountType(AccountType.BANK).owner(self).currency(Currency.SGD).description("CIMB Savings").build());
        accountRepository.save(Account.builder().name("Coinhako").accountType(AccountType.CRYPTO_EXCHANGE).owner(self).currency(Currency.SGD).description("Coinhako crypto exchange").build());
        accountRepository.save(Account.builder().name("Crypto.com").accountType(AccountType.CRYPTO_EXCHANGE).owner(self).currency(Currency.SGD).description("Crypto.com wallet").build());
        accountRepository.save(Account.builder().name("SL-Fixed").accountType(AccountType.BANK).owner(self).currency(Currency.LKR).description("Sri Lanka Fixed Deposits").build());

        // Spouse accounts
        accountRepository.save(Account.builder().name("Tiger-Spouse").accountType(AccountType.BROKER).owner(spouse).currency(Currency.USD).description("Tiger Brokers - Spouse").build());
        accountRepository.save(Account.builder().name("Saxo-Spouse").accountType(AccountType.BROKER).owner(spouse).currency(Currency.SGD).description("Saxo - Spouse").build());

        // ─── Banks (Sri Lanka for FDs) ───
        bankRepository.save(Bank.builder().name("National Savings Bank").shortName("NSB").build());
        bankRepository.save(Bank.builder().name("Bank of Ceylon").shortName("BOC").build());
        bankRepository.save(Bank.builder().name("Commercial Bank").shortName("Commercial").build());
        bankRepository.save(Bank.builder().name("Seylan Bank").shortName("Seylan").build());
        bankRepository.save(Bank.builder().name("People's Bank").shortName("Peoples").build());
        bankRepository.save(Bank.builder().name("Hatton National Bank").shortName("HNB").build());
        bankRepository.save(Bank.builder().name("Sampath Bank").shortName("Sampath").build());

        // ─── FD Holders ───
        fdHolderRepository.save(FDHolder.builder().name("Parent A").relationship("Mother").isSeniorCitizen(true).build());
        fdHolderRepository.save(FDHolder.builder().name("Parent B").relationship("Father").isSeniorCitizen(true).build());
        fdHolderRepository.save(FDHolder.builder().name("Self/Parent B").relationship("Self/Father").isSeniorCitizen(false).build());
        fdHolderRepository.save(FDHolder.builder().name("Self/Parent A").relationship("Self/Mother").isSeniorCitizen(false).build());
        fdHolderRepository.save(FDHolder.builder().name("Sibling1/Parent B").relationship("Sibling/Father").isSeniorCitizen(false).build());
        fdHolderRepository.save(FDHolder.builder().name("Sibling1/Parent A").relationship("Sibling/Mother").isSeniorCitizen(false).build());
        fdHolderRepository.save(FDHolder.builder().name("Sibling 1").relationship("Sibling").isSeniorCitizen(false).build());
        fdHolderRepository.save(FDHolder.builder().name("Sibling2/Parent B").relationship("Sibling/Father").isSeniorCitizen(false).build());
        fdHolderRepository.save(FDHolder.builder().name("Sibling2/Parent A").relationship("Sibling/Mother").isSeniorCitizen(false).build());

        // ─── Currency Rates ───
        currencyRateRepository.save(CurrencyRate.builder().fromCurrency(Currency.USD).toCurrency(Currency.SGD).rate(new BigDecimal("1.35")).effectiveDate(LocalDate.now()).build());
        currencyRateRepository.save(CurrencyRate.builder().fromCurrency(Currency.EUR).toCurrency(Currency.SGD).rate(new BigDecimal("1.45")).effectiveDate(LocalDate.now()).build());
        currencyRateRepository.save(CurrencyRate.builder().fromCurrency(Currency.LKR).toCurrency(Currency.SGD).rate(new BigDecimal("0.004")).effectiveDate(LocalDate.now()).build());

        // ─── Allocation Targets (for primary owner) ───
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.INDEX_FUND).targetPercentage(new BigDecimal("37")).targetAmount(new BigDecimal("0")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.GROWTH_EQUITY).targetPercentage(new BigDecimal("23")).targetAmount(new BigDecimal("0")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.DIVIDEND_EQUITY).targetPercentage(new BigDecimal("10")).targetAmount(new BigDecimal("0")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.MONEY_MARKET).targetPercentage(new BigDecimal("10")).targetAmount(new BigDecimal("0")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.LEVERAGED_ETF).targetPercentage(new BigDecimal("5")).targetAmount(new BigDecimal("0")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.FIXED_DEPOSIT).targetPercentage(new BigDecimal("5")).targetAmount(new BigDecimal("0")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.CRYPTO).targetPercentage(new BigDecimal("5")).targetAmount(new BigDecimal("0")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.SAVINGS).targetPercentage(new BigDecimal("3")).targetAmount(new BigDecimal("0")).build());
        allocationTargetRepository.save(AllocationTarget.builder().owner(self).assetType(AssetType.MUTUAL_FUND).targetPercentage(new BigDecimal("2")).targetAmount(new BigDecimal("0")).build());

        System.out.println("Reference data initialized (owners, accounts, banks, FD holders, currency rates, allocation targets)");
    }
}
