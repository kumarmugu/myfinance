package com.myfinance.service;

import com.myfinance.model.Holding;
import com.myfinance.model.enums.AssetType;
import com.myfinance.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingService {
    private final HoldingRepository holdingRepository;

    public List<Holding> getActiveHoldings() { return holdingRepository.findActiveHoldings(); }
    public List<Holding> getActiveByOwner(Long ownerId) { return holdingRepository.findActiveHoldingsByOwner(ownerId); }
    public List<Holding> getByAccount(Long accountId) { return holdingRepository.findByAccountId(accountId); }
    public List<Holding> getByAssetType(AssetType type) { return holdingRepository.findByAssetType(type); }
    public Optional<Holding> getHolding(Long assetId, Long accountId, Long ownerId) {
        return holdingRepository.findByAssetIdAndAccountIdAndOwnerId(assetId, accountId, ownerId);
    }
    public Holding save(Holding holding) {
        Holding saved = holdingRepository.save(holding);
        log.info("Saved Holding id={} assetId={} quantity={}", saved.getId(), saved.getAsset().getId(), saved.getQuantity());
        return saved;
    }
}
