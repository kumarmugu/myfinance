package com.myfinance.repository;

import com.myfinance.model.PreciousMetal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PreciousMetalRepository extends JpaRepository<PreciousMetal, Long> {
    List<PreciousMetal> findByUserIdOrderByPurchaseDateDesc(Long userId);
    List<PreciousMetal> findByUserIdAndMetalType(Long userId, String metalType);
    List<PreciousMetal> findByUserIdAndStatus(Long userId, String status);
}
