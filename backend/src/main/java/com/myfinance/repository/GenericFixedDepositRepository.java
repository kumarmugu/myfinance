package com.myfinance.repository;

import com.myfinance.model.GenericFixedDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GenericFixedDepositRepository extends JpaRepository<GenericFixedDeposit, Long> {
    List<GenericFixedDeposit> findByUserIdOrderByMaturityDateAsc(Long userId);
    List<GenericFixedDeposit> findByUserIdAndStatus(Long userId, String status);
}
