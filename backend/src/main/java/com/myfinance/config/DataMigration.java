package com.myfinance.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time data migration: assigns all records with userId=NULL to the first non-admin user.
 * This fixes data that was created before multi-tenant isolation was implemented.
 * Safe to run multiple times — only affects NULL userId records.
 */
@Component
@Order(3)
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.init-data", havingValue = "true", matchIfMissing = true)
public class DataMigration implements CommandLineRunner {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void run(String... args) {
        // Find the first non-admin user (the primary user)
        var result = em.createQuery("SELECT u.id FROM AppUser u WHERE u.role = 'USER' ORDER BY u.id ASC", Long.class)
                .setMaxResults(1)
                .getResultList();

        if (result.isEmpty()) {
            log.info("DataMigration: No USER found, skipping");
            return;
        }

        Long targetUserId = result.get(0);
        log.info("DataMigration: Assigning orphan records (userId=NULL) to userId={}", targetUserId);

        int total = 0;
        total += update("UPDATE FixedDeposit SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE Account SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE Transaction SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE Holding SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE Dividend SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE SoldPosition SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE AccountDeposit SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE SalaryRecord SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE TaxRecord SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE WorkExperience SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE HomeLoan SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE InsurancePolicy SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE RetirementFundEntry SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE BankSavings SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE AllocationTarget SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE NetWorthSnapshot SET userId = :uid WHERE userId IS NULL", targetUserId);
        total += update("UPDATE Asset SET userId = :uid WHERE userId IS NULL", targetUserId);

        if (total > 0) {
            log.info("DataMigration: Updated {} orphan records to userId={}", total, targetUserId);
        } else {
            log.debug("DataMigration: No orphan records found");
        }

        backfillBankSavingsOwner();
    }

    /**
     * Bank Savings now requires an Owner. Backfill any existing account with owner=NULL to its
     * own user's SELF owner. Per user, so multi-tenant data stays isolated. Idempotent — only
     * touches NULL-owner rows.
     */
    private void backfillBankSavingsOwner() {
        // Map each userId that has a SELF owner to that owner's id.
        var selfOwners = em.createQuery(
                "SELECT o.userId, o.id FROM Owner o WHERE o.relationship = 'SELF' AND o.isActive = true",
                Object[].class).getResultList();

        int updated = 0;
        for (Object[] row : selfOwners) {
            Long ownerUserId = (Long) row[0];
            Long selfOwnerId = (Long) row[1];
            if (ownerUserId == null || selfOwnerId == null) {
                continue;
            }
            updated += em.createQuery(
                    "UPDATE BankSavings b SET b.owner.id = :oid WHERE b.owner IS NULL AND b.userId = :uid")
                    .setParameter("oid", selfOwnerId)
                    .setParameter("uid", ownerUserId)
                    .executeUpdate();
        }
        if (updated > 0) {
            log.info("DataMigration: Backfilled owner (SELF) on {} BankSavings account(s)", updated);
        }
    }

    private int update(String jpql, Long userId) {
        return em.createQuery(jpql).setParameter("uid", userId).executeUpdate();
    }
}
