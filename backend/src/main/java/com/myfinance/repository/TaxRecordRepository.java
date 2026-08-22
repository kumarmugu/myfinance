package com.myfinance.repository;

import com.myfinance.model.TaxRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaxRecordRepository extends JpaRepository<TaxRecord, Long> {
    List<TaxRecord> findAllByOrderByAssessmentYearDesc();
    List<TaxRecord> findByOwnerIdOrderByAssessmentYearDesc(Long ownerId);
    List<TaxRecord> findByCountryOrderByAssessmentYearDesc(String country);
    List<TaxRecord> findByUserIdOrderByAssessmentYearDesc(Long userId);
}
