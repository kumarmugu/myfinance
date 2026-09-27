package com.myfinance.repository;

import com.myfinance.model.LevEtfAlertPref;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LevEtfAlertPrefRepository extends JpaRepository<LevEtfAlertPref, Long> {
    List<LevEtfAlertPref> findByUserId(Long userId);
}
