package com.myfinance.service;

import com.myfinance.model.Holding;
import com.myfinance.model.enums.AssetType;
import com.myfinance.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HoldingService {

    private final HoldingRepository holdingRepository;

    public List<Holding> getAllHoldings() {
        return holdingRepository.findAll();
    }

    public List<Holding> getActiveHoldings() {
        return holdingRepository.findActiveHoldings();
    }

    public List<Holding> getHoldingsByAccount(Long accountId) {
        return holdingRepository.findByAccountId(accountId);
    }

    public List<Holding> getHoldingsByAssetType(AssetType assetType) {
        return holdingRepository.findByAssetType(assetType);
    }

    public Optional<Holding> getHolding(Long assetId, Long accountId) {
        return holdingRepository.findByAssetIdAndAccountId(assetId, accountId);
    }

    public Holding saveHolding(Holding holding) {
        return holdingRepository.save(holding);
    }
}
