package com.myfinance.repository;

import com.myfinance.model.NetWorthConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NetWorthConfigRepository extends JpaRepository<NetWorthConfig, Long> {
    Optional<NetWorthConfig> findByAssetType(String assetType);
    List<NetWorthConfig> findByIncludeInNetWorthTrue();
    List<NetWorthConfig> findAllByOrderByAssetTypeAsc();
    List<NetWorthConfig> findByUserIdOrderByAssetTypeAsc(Long userId);
    List<NetWorthConfig> findByUserIdAndIncludeInNetWorthTrue(Long userId);
}
