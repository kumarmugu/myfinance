package com.myfinance.repository;

import com.myfinance.model.FDHolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FDHolderRepository extends JpaRepository<FDHolder, Long> {
}
