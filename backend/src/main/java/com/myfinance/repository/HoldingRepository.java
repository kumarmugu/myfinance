package com.myfinance.repository;

import com.myfinance.model.Holding;
import com.myfinance.model.enums.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {
    Optional<Holding> findByAssetIdAndAccountIdAndOwnerId(Long assetId, Long accountId, Long ownerId);
    List<Holding> findByAccountId(Long accountId);
    List<Holding> findByOwnerId(Long ownerId);

    @Query("SELECT h FROM Holding h WHERE h.quantity > 0")
    List<Holding> findActiveHoldings();

    @Query("SELECT h FROM Holding h WHERE h.quantity > 0 AND h.owner.id = :ownerId")
    List<Holding> findActiveHoldingsByOwner(@Param("ownerId") Long ownerId);

    @Query("SELECT h FROM Holding h JOIN h.asset a WHERE a.assetType = :assetType AND h.quantity > 0")
    List<Holding> findByAssetType(@Param("assetType") AssetType assetType);
}
