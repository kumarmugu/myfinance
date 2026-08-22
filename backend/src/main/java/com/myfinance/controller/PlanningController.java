package com.myfinance.controller;

import com.myfinance.model.AccountDeposit;
import com.myfinance.model.AllocationTarget;
import com.myfinance.repository.AllocationTargetRepository;
import com.myfinance.security.TenantContext;
import com.myfinance.service.AccountDepositService;
import com.myfinance.service.NetWorthService;
import lombok.RequiredArgsConstructor;
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
        return allocationTargetRepository.saveAll(targets);
    }

    @GetMapping("/deposits")
    public List<AccountDeposit> getDeposits(@RequestParam(required = false) Long accountId) {
        return accountId != null ? accountDepositService.getByAccount(accountId) : accountDepositService.getAll();
    }

    @PostMapping("/deposits")
    public ResponseEntity<AccountDeposit> createDeposit(@RequestBody AccountDeposit deposit) {
        deposit.setUserId(tenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountDepositService.create(deposit));
    }

    @DeleteMapping("/deposits/{id}")
    public ResponseEntity<Void> deleteDeposit(@PathVariable Long id) { accountDepositService.delete(id); return ResponseEntity.noContent().build(); }
}
