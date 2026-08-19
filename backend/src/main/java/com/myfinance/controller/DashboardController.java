package com.myfinance.controller;

import com.myfinance.dto.DashboardSummary;
import com.myfinance.model.NetWorthSnapshot;
import com.myfinance.service.DashboardService;
import com.myfinance.service.NetWorthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;
    private final NetWorthService netWorthService;

    @GetMapping("/summary")
    public DashboardSummary getSummary(@RequestParam(required = false) Long ownerId) {
        return dashboardService.getSummary(ownerId);
    }

    @GetMapping("/allocation")
    public Map<String, BigDecimal> getAllocation() { return netWorthService.getCurrentAllocation(); }

    @PostMapping("/snapshot")
    public NetWorthSnapshot takeSnapshot(@RequestParam(required = false) Long ownerId) { return netWorthService.takeSnapshot(ownerId); }

    @GetMapping("/net-worth/history")
    public List<NetWorthSnapshot> getHistory(@RequestParam(required = false) Long ownerId) {
        return ownerId != null ? netWorthService.getByOwner(ownerId) : netWorthService.getHistory();
    }

    @GetMapping("/net-worth/latest")
    public ResponseEntity<NetWorthSnapshot> getLatest() {
        return netWorthService.getLatest().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }
}
