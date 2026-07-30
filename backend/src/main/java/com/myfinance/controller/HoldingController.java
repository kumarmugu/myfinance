package com.myfinance.controller;

import com.myfinance.model.Holding;
import com.myfinance.model.enums.AssetType;
import com.myfinance.service.HoldingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/holdings")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingService holdingService;

    @GetMapping
    public List<Holding> getActiveHoldings() {
        return holdingService.getActiveHoldings();
    }

    @GetMapping("/all")
    public List<Holding> getAllHoldings() {
        return holdingService.getAllHoldings();
    }

    @GetMapping("/account/{accountId}")
    public List<Holding> getByAccount(@PathVariable Long accountId) {
        return holdingService.getHoldingsByAccount(accountId);
    }

    @GetMapping("/type/{assetType}")
    public List<Holding> getByAssetType(@PathVariable AssetType assetType) {
        return holdingService.getHoldingsByAssetType(assetType);
    }
}
