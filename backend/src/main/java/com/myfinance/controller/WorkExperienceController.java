package com.myfinance.controller;

import com.myfinance.model.WorkExperience;
import com.myfinance.repository.WorkExperienceRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/work-experience")
@RequiredArgsConstructor
@Slf4j
public class WorkExperienceController {
    private final WorkExperienceRepository repository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<WorkExperience> getAll(@RequestParam(required = false) Long ownerId) {
        Long uid = tenantContext.getCurrentUserId();
        if (ownerId != null) return repository.findByOwnerIdOrderByStartDateDesc(ownerId);
        return repository.findByUserIdOrderByStartDateDesc(uid);
    }

    @GetMapping("/{id}")
    public WorkExperience getById(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
    }

    @PostMapping
    public ResponseEntity<WorkExperience> create(@RequestBody WorkExperience exp) {
        log.info("Creating work experience: company={}, position={}", exp.getCompany(), exp.getPosition());
        exp.setUserId(tenantContext.getCurrentUserId());
        WorkExperience saved = repository.save(exp);
        log.info("Created work experience id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public WorkExperience update(@PathVariable Long id, @RequestBody WorkExperience updated) {
        log.info("Updating work experience id={}", id);
        WorkExperience existing = getById(id);
        existing.setCompany(updated.getCompany());
        existing.setPosition(updated.getPosition());
        existing.setLevel(updated.getLevel());
        existing.setCountry(updated.getCountry());
        existing.setStartDate(updated.getStartDate());
        existing.setEndDate(updated.getEndDate());
        existing.setIsCurrent(updated.getIsCurrent());
        existing.setIndustry(updated.getIndustry());
        existing.setNotes(updated.getNotes());
        existing.setOwner(updated.getOwner());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting work experience id={}", id);
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
