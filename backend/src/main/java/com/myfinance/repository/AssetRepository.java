package com.myfinance.repository;

import com.myfinance.model.Asset;
import com.myfinance.model.enums.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    Optional<Asset> findBySymbol(String symbol);
    List<Asset> findByAssetType(AssetType assetType);
    List<Asset> findByNameContainingIgnoreCaseOrSymbolContainingIgnoreCase(String name, String symbol);
}
