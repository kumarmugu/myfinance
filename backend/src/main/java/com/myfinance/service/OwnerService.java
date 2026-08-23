package com.myfinance.service;

import com.myfinance.config.ReferenceConstraintException;
import com.myfinance.model.Owner;
import com.myfinance.repository.AccountRepository;
import com.myfinance.repository.HoldingRepository;
import com.myfinance.repository.OwnerRepository;
import com.myfinance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerService {
    private final OwnerRepository ownerRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final HoldingRepository holdingRepository;

    public List<Owner> getAllOwners() { return ownerRepository.findByIsActiveTrue(); }
    public List<Owner> getByUserId(Long userId) { return ownerRepository.findByUserIdAndIsActiveTrue(userId); }
    public Owner getById(Long id) { return ownerRepository.findById(id).orElseThrow(() -> new RuntimeException("Owner not found: " + id)); }
    public Owner create(Owner owner) {
        Owner saved = ownerRepository.save(owner);
        log.info("Created Owner id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    public Owner update(Long id, Owner updated) {
        Owner existing = getById(id);
        existing.setName(updated.getName());
        existing.setRelationship(updated.getRelationship());
        Owner saved = ownerRepository.save(existing);
        log.info("Updated Owner id={}", id);
        return saved;
    }

    public void delete(Long id) {
        Owner owner = getById(id);
        List<String> references = new ArrayList<>();

        long accountCount = accountRepository.findByOwnerId(id).size();
        if (accountCount > 0) references.add(accountCount + " Account(s)");

        long holdingCount = holdingRepository.findByOwnerId(id).size();
        if (holdingCount > 0) references.add(holdingCount + " Holding(s)");

        long txCount = transactionRepository.findByOwnerIdOrderByTransactionDateDesc(id).size();
        if (txCount > 0) references.add(txCount + " Transaction(s)");

        if (!references.isEmpty()) {
            log.warn("Cannot delete Owner id={}, referenced by: {}", id, references);
            throw new ReferenceConstraintException("Owner '" + owner.getName() + "'", references);
        }

        owner.setIsActive(false);
        ownerRepository.save(owner);
        log.info("Soft-deleted Owner id={}", id);
    }
}
