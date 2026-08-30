package com.myfinance.repository;

import com.myfinance.model.Bond;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BondRepository extends JpaRepository<Bond, Long> {
    List<Bond> findByUserIdOrderByMaturityDateAsc(Long userId);
    List<Bond> findByUserIdAndStatus(Long userId, String status);
    List<Bond> findByOwnerIdOrderByMaturityDateAsc(Long ownerId);
}
