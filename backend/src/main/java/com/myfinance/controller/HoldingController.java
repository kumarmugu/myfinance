package com.myfinance.controller;

import com.myfinance.model.Holding;
import com.myfinance.model.enums.AssetType;
import com.myfinance.security.TenantContext;
import com.myfinance.service.HoldingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/holdings")
@RequiredArgsConstructor
@Slf4j
public class HoldingController {
    private final HoldingService holdingService;
    private final TenantContext tenantContext;

    @GetMapping
    public List<Holding> getActive(@RequestParam(required = false) Long ownerId) {
        log.debug("Fetching active holdings, ownerId={}", ownerId);
        return ownerId != null ? holdingService.getActiveByOwner(ownerId) : holdingService.getActiveHoldings();
    }

    @GetMapping("/account/{accountId}")
    public List<Holding> getByAccount(@PathVariable Long accountId) { return holdingService.getByAccount(accountId); }

    @GetMapping("/type/{type}")
    public List<Holding> getByType(@PathVariable AssetType type) { return holdingService.getByAssetType(type); }
}
