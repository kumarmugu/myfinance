package com.myfinance.controller;

import com.myfinance.model.WorkExperience;
import com.myfinance.repository.WorkExperienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/work-experience")
@RequiredArgsConstructor
public class WorkExperienceController {
    private final WorkExperienceRepository repository;

    @GetMapping
    public List<WorkExperience> getAll(@RequestParam(required = false) Long ownerId) {
        if (ownerId != null) return repository.findByOwnerIdOrderByStartDateDesc(ownerId);
        return repository.findAllByOrderByStartDateDesc();
    }

    @GetMapping("/{id}")
    public WorkExperience getById(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
    }

    @PostMapping
    public ResponseEntity<WorkExperience> create(@RequestBody WorkExperience exp) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(exp));
    }

    @PutMapping("/{id}")
    public WorkExperience update(@PathVariable Long id, @RequestBody WorkExperience updated) {
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
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
