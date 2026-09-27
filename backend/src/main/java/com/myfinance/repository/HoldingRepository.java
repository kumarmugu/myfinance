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

    /**
     * Purpose-aware position lookup. A holding's identity is asset + account + owner + purpose, so a
     * cash-funded position (e.g. LONG_TERM) and an SRS-funded position (SRS) of the same symbol are
     * separate rows. Handles a null purpose (legacy/unspecified) with an explicit IS NULL branch,
     * since a JPQL equality never matches null.
     */
    @Query("SELECT h FROM Holding h WHERE h.asset.id = :assetId AND h.account.id = :accountId "
            + "AND h.owner.id = :ownerId AND (h.purpose = :purpose OR (:purpose IS NULL AND h.purpose IS NULL))")
    Optional<Holding> findByPosition(@Param("assetId") Long assetId, @Param("accountId") Long accountId,
                                     @Param("ownerId") Long ownerId,
                                     @Param("purpose") com.myfinance.model.enums.InvestmentPurpose purpose);
    List<Holding> findByUserId(Long userId);
    List<Holding> findByAccountId(Long accountId);
    List<Holding> findByOwnerId(Long ownerId);
    List<Holding> findByAssetId(Long assetId);

    @Query("SELECT h FROM Holding h WHERE h.quantity > 0")
    List<Holding> findActiveHoldings();

    @Query("SELECT h FROM Holding h WHERE h.quantity > 0 AND h.owner.id = :ownerId")
    List<Holding> findActiveHoldingsByOwner(@Param("ownerId") Long ownerId);

    @Query("SELECT h FROM Holding h JOIN h.asset a WHERE a.assetType = :assetType AND h.quantity > 0")
    List<Holding> findByAssetType(@Param("assetType") AssetType assetType);

    @Query("SELECT h FROM Holding h WHERE h.quantity > 0 AND h.userId = :userId")
    List<Holding> findActiveHoldingsByUserId(@Param("userId") Long userId);

    @Query("SELECT h FROM Holding h WHERE h.quantity > 0 AND h.userId = :userId AND h.account.id = :accountId")
    List<Holding> findActiveByUserIdAndAccountId(@Param("userId") Long userId, @Param("accountId") Long accountId);

    @Query("SELECT h FROM Holding h JOIN h.asset a WHERE a.assetType = :assetType AND h.quantity > 0 AND h.userId = :userId")
    List<Holding> findByAssetTypeAndUserId(@Param("assetType") AssetType assetType, @Param("userId") Long userId);
}
