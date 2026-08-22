package com.myfinance.repository;

import com.myfinance.model.AllocationTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AllocationTargetRepository extends JpaRepository<AllocationTarget, Long> {
    List<AllocationTarget> findByOwnerId(Long ownerId);
    List<AllocationTarget> findByUserId(Long userId);
}
