package com.myfinance.controller;

import com.myfinance.dto.DashboardSummary;
import com.myfinance.model.NetWorthSnapshot;
import com.myfinance.service.DashboardService;
import com.myfinance.service.NetWorthService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final NetWorthService netWorthService;

    @GetMapping("/summary")
    public DashboardSummary getSummary() {
        return dashboardService.getDashboardSummary();
    }

    @GetMapping("/allocation")
    public Map<String, BigDecimal> getAllocation() {
        return netWorthService.getCurrentAllocation();
    }

    @PostMapping("/snapshot")
    public NetWorthSnapshot takeSnapshot() {
        return netWorthService.calculateAndSaveSnapshot();
    }

    @GetMapping("/net-worth/history")
    public List<NetWorthSnapshot> getNetWorthHistory() {
        return netWorthService.getNetWorthHistory();
    }

    @GetMapping("/net-worth/history/range")
    public List<NetWorthSnapshot> getNetWorthHistoryRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return netWorthService.getNetWorthHistoryBetween(start, end);
    }

    @GetMapping("/net-worth/latest")
    public ResponseEntity<NetWorthSnapshot> getLatestSnapshot() {
        return netWorthService.getLatestSnapshot()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
