package com.myfinance.service;

import com.myfinance.model.FixedDeposit;
import com.myfinance.model.enums.FDStatus;
import com.myfinance.repository.FixedDepositRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FixedDepositService {
    private final FixedDepositRepository fdRepository;

    public List<FixedDeposit> getAll() { return fdRepository.findAll(); }
    public FixedDeposit getById(Long id) { return fdRepository.findById(id).orElseThrow(() -> new RuntimeException("FD not found: " + id)); }
    public List<FixedDeposit> getByStatus(FDStatus status) { return fdRepository.findByStatus(status); }
    public List<FixedDeposit> getByHolder(Long holderId) { return fdRepository.findByHolderId(holderId); }
    public List<FixedDeposit> getByBank(Long bankId) { return fdRepository.findByBankId(bankId); }
    public List<FixedDeposit> getActiveByMaturity() { return fdRepository.findAllActiveOrderByMaturity(); }
    public List<FixedDeposit> getRequiringUpdate() { return fdRepository.findByRequiresUpdateTrue(); }

    public List<FixedDeposit> getMaturingWithinDays(int days) {
        return fdRepository.findMaturingBefore(LocalDate.now().plusDays(days));
    }

    public FixedDeposit create(FixedDeposit fd) {
        if (fd.getExpectedInterest() == null) {
            fd.setExpectedInterest(calculateInterest(fd));
        }
        return fdRepository.save(fd);
    }

    public FixedDeposit update(Long id, FixedDeposit updated) {
        FixedDeposit existing = getById(id);
        existing.setHolder(updated.getHolder());
        existing.setJointHolder(updated.getJointHolder());
        existing.setBank(updated.getBank());
        existing.setAccountNumber(updated.getAccountNumber());
        existing.setPrincipalAmount(updated.getPrincipalAmount());
        existing.setInterestRate(updated.getInterestRate());
        existing.setStartDate(updated.getStartDate());
        existing.setMaturityDate(updated.getMaturityDate());
        existing.setPeriod(updated.getPeriod());
        existing.setBranch(updated.getBranch());
        existing.setCategory(updated.getCategory());
        existing.setStatus(updated.getStatus());
        existing.setBeneficiary(updated.getBeneficiary());
        existing.setPurpose(updated.getPurpose());
        existing.setNotes(updated.getNotes());
        existing.setRequiresUpdate(updated.getRequiresUpdate());
        existing.setExpectedInterest(calculateInterest(existing));
        return fdRepository.save(existing);
    }

    public void delete(Long id) { fdRepository.deleteById(id); }

    public BigDecimal calculateInterest(FixedDeposit fd) {
        long days = ChronoUnit.DAYS.between(fd.getStartDate(), fd.getMaturityDate());
        return fd.getPrincipalAmount()
                .multiply(fd.getInterestRate())
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(36500), 2, RoundingMode.HALF_UP);
    }

    public long getDaysToMaturity(FixedDeposit fd) {
        return ChronoUnit.DAYS.between(LocalDate.now(), fd.getMaturityDate());
    }
}
