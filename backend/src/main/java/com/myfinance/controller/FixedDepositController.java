package com.myfinance.controller;

import com.myfinance.model.Bank;
import com.myfinance.model.FDHolder;
import com.myfinance.model.FixedDeposit;
import com.myfinance.model.enums.FDStatus;
import com.myfinance.repository.BankRepository;
import com.myfinance.repository.FDHolderRepository;
import com.myfinance.security.TenantContext;
import com.myfinance.service.FixedDepositService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fixed-deposits")
@RequiredArgsConstructor
@Slf4j
public class FixedDepositController {
    private final FixedDepositService fdService;
    private final BankRepository bankRepository;
    private final FDHolderRepository fdHolderRepository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<FixedDeposit> getAll(
            @RequestParam(required = false) Long holderId,
            @RequestParam(required = false) Long bankId,
            @RequestParam(required = false) FDStatus status) {
        if (holderId != null) return fdService.getByHolder(holderId);
        if (bankId != null) return fdService.getByBank(bankId);
        if (status != null) return fdService.getByStatus(status);
        return fdService.getAll();
    }

    @GetMapping("/{id}")
    public FixedDeposit getById(@PathVariable Long id) { return fdService.getById(id); }

    @GetMapping("/maturing")
    public List<FixedDeposit> getMaturing(@RequestParam(defaultValue = "90") int days) { return fdService.getMaturingWithinDays(days); }

    @GetMapping("/requires-update")
    public List<FixedDeposit> getRequiringUpdate() { return fdService.getRequiringUpdate(); }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        List<FixedDeposit> all = fdService.getAll();
        List<FixedDeposit> active = all.stream().filter(fd -> fd.getStatus() == FDStatus.ACTIVE).collect(Collectors.toList());

        BigDecimal totalPrincipal = active.stream().map(FixedDeposit::getPrincipalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInterest = active.stream()
                .map(fd -> fd.getExpectedInterest() != null ? fd.getExpectedInterest() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> byBank = new HashMap<>();
        active.stream().collect(Collectors.groupingBy(fd -> fd.getBank().getShortName(),
                Collectors.reducing(BigDecimal.ZERO, FixedDeposit::getPrincipalAmount, BigDecimal::add)))
                .forEach(byBank::put);

        Map<String, Object> result = new HashMap<>();
        result.put("totalFDs", active.size());
        result.put("totalPrincipal", totalPrincipal);
        result.put("totalExpectedInterest", totalInterest);
        result.put("byBank", byBank);
        result.put("maturingWithin30Days", fdService.getMaturingWithinDays(30).size());
        result.put("maturingWithin90Days", fdService.getMaturingWithinDays(90).size());
        result.put("requiresUpdate", fdService.getRequiringUpdate().size());

        // Net worth inclusion
        BigDecimal includedInNetWorth = active.stream()
                .filter(fd -> Boolean.TRUE.equals(fd.getIncludeInNetWorth()) && fd.getNetWorthAmount() != null)
                .map(FixedDeposit::getNetWorthAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("includedInNetWorth", includedInNetWorth);
        long includedCount = active.stream().filter(fd -> Boolean.TRUE.equals(fd.getIncludeInNetWorth())).count();
        result.put("includedInNetWorthCount", includedCount);

        return result;
    }

    @PatchMapping("/{id}/net-worth")
    public FixedDeposit toggleNetWorthInclusion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        log.info("Updating net-worth inclusion for fixed deposit id={}", id);
        FixedDeposit fd = fdService.getById(id);
        if (body.containsKey("includeInNetWorth")) {
            fd.setIncludeInNetWorth((Boolean) body.get("includeInNetWorth"));
        }
        if (body.containsKey("netWorthAmount")) {
            fd.setNetWorthAmount(body.get("netWorthAmount") != null ? new BigDecimal(body.get("netWorthAmount").toString()) : null);
        }
        return fdService.create(fd); // save changes
    }

    @PostMapping
    public ResponseEntity<FixedDeposit> create(@Valid @RequestBody FixedDeposit fd) {
        log.info("Creating fixed deposit: bank={}, principal={}", fd.getBank() != null ? fd.getBank().getShortName() : "N/A", fd.getPrincipalAmount());
        fd.setUserId(tenantContext.getCurrentUserId());
        FixedDeposit saved = fdService.create(fd);
        log.info("Created fixed deposit id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public FixedDeposit update(@PathVariable Long id, @Valid @RequestBody FixedDeposit fd) {
        log.info("Updating fixed deposit id={}", id);
        return fdService.update(id, fd);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting fixed deposit id={}", id);
        fdService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Reference Data ───
    @GetMapping("/banks")
    public List<Bank> getBanks() { return bankRepository.findAll(); }

    @GetMapping("/holders")
    public List<FDHolder> getHolders() { return fdHolderRepository.findAll(); }

    @PostMapping("/banks")
    public Bank createBank(@RequestBody Bank bank) {
        log.info("Creating bank: {}", bank.getShortName());
        return bankRepository.save(bank);
    }

    @PostMapping("/holders")
    public FDHolder createHolder(@RequestBody FDHolder holder) {
        log.info("Creating FD holder: {}", holder.getName());
        return fdHolderRepository.save(holder);
    }
}
