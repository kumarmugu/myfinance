package com.myfinance.repository;

import com.myfinance.model.WorkExperience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkExperienceRepository extends JpaRepository<WorkExperience, Long> {
    List<WorkExperience> findAllByOrderByStartDateDesc();
    List<WorkExperience> findByOwnerIdOrderByStartDateDesc(Long ownerId);
}
