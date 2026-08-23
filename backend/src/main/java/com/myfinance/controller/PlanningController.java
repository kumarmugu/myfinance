package com.myfinance.controller;

import com.myfinance.model.AccountDeposit;
import com.myfinance.model.AllocationTarget;
import com.myfinance.repository.AllocationTargetRepository;
import com.myfinance.security.TenantContext;
import com.myfinance.service.AccountDepositService;
import com.myfinance.service.NetWorthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/planning")
@RequiredArgsConstructor
@Slf4j
public class PlanningController {
    private final AllocationTargetRepository allocationTargetRepository;
    private final NetWorthService netWorthService;
    private final AccountDepositService accountDepositService;
    private final TenantContext tenantContext;

    @GetMapping("/allocation")
    public Map<String, Object> getAllocation(@RequestParam(required = false) Long ownerId) {
        List<AllocationTarget> targets = ownerId != null
                ? allocationTargetRepository.findByOwnerId(ownerId)
                : allocationTargetRepository.findAll();
        Map<String, BigDecimal> current = netWorthService.getCurrentAllocation();

        Map<String, Object> result = new HashMap<>();
        result.put("targets", targets);
        result.put("current", current);
        return result;
    }

    @PutMapping("/allocation")
    public List<AllocationTarget> updateTargets(@RequestBody List<AllocationTarget> targets) {
        log.info("Updating {} allocation targets", targets.size());
        return allocationTargetRepository.saveAll(targets);
    }

    @GetMapping("/deposits")
    public List<AccountDeposit> getDeposits(@RequestParam(required = false) Long accountId) {
        return accountId != null ? accountDepositService.getByAccount(accountId) : accountDepositService.getAll();
    }

    @PostMapping("/deposits")
    public ResponseEntity<AccountDeposit> createDeposit(@RequestBody AccountDeposit deposit) {
        log.info("Creating deposit: amount={}", deposit.getAmount());
        deposit.setUserId(tenantContext.getCurrentUserId());
        AccountDeposit saved = accountDepositService.create(deposit);
        log.info("Created deposit id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/deposits/{id}")
    public ResponseEntity<Void> deleteDeposit(@PathVariable Long id) {
        log.info("Deleting deposit id={}", id);
        accountDepositService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
