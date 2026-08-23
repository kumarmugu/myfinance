package com.myfinance.controller;

import com.myfinance.dto.DashboardSummary;
import com.myfinance.model.NetWorthSnapshot;
import com.myfinance.security.TenantContext;
import com.myfinance.service.DashboardService;
import com.myfinance.service.NetWorthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {
    private final DashboardService dashboardService;
    private final NetWorthService netWorthService;
    private final TenantContext tenantContext;

    @GetMapping("/summary")
    public DashboardSummary getSummary(@RequestParam(required = false) Long ownerId) {
        Long uid = tenantContext.getCurrentUserId();
        return dashboardService.getSummary(ownerId, uid);
    }

    @GetMapping("/allocation")
    public Map<String, BigDecimal> getAllocation() {
        Long uid = tenantContext.getCurrentUserId();
        return netWorthService.getCurrentAllocationForUser(uid);
    }

    @PostMapping("/snapshot")
    public NetWorthSnapshot takeSnapshot(@RequestParam(required = false) Long ownerId) {
        Long uid = tenantContext.getCurrentUserId();
        log.info("Taking net-worth snapshot for ownerId={}, userId={}", ownerId, uid);
        return netWorthService.takeSnapshot(ownerId, uid);
    }

    @GetMapping("/net-worth/history")
    public List<NetWorthSnapshot> getHistory(@RequestParam(required = false) Long ownerId) {
        return ownerId != null ? netWorthService.getByOwner(ownerId) : netWorthService.getHistory();
    }

    @GetMapping("/net-worth/latest")
    public ResponseEntity<NetWorthSnapshot> getLatest() {
        return netWorthService.getLatest().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }
}
