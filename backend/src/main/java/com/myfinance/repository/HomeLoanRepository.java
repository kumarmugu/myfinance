package com.myfinance.repository;

import com.myfinance.model.HomeLoan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HomeLoanRepository extends JpaRepository<HomeLoan, Long> {
    List<HomeLoan> findByIsActiveTrueOrderByPropertyNameAsc();
    List<HomeLoan> findByOwnerIdOrderByPropertyNameAsc(Long ownerId);
}
