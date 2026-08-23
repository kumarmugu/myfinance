package com.myfinance.controller;

import com.myfinance.model.HomeLoan;
import com.myfinance.model.LoanPayment;
import com.myfinance.repository.HomeLoanRepository;
import com.myfinance.repository.LoanPaymentRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/home-loans")
@RequiredArgsConstructor
@Slf4j
public class HomeLoanController {
    private final HomeLoanRepository loanRepository;
    private final LoanPaymentRepository paymentRepository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<HomeLoan> getAll(@RequestParam(required = false) Long ownerId) {
        Long uid = tenantContext.getCurrentUserId();
        if (ownerId != null) return loanRepository.findByOwnerIdOrderByPropertyNameAsc(ownerId);
        return loanRepository.findByUserIdAndIsActiveTrue(uid);
    }

    @GetMapping("/{id}")
    public HomeLoan getById(@PathVariable Long id) {
        return loanRepository.findById(id).orElseThrow(() -> new RuntimeException("Loan not found"));
    }

    @PostMapping
    public ResponseEntity<HomeLoan> create(@RequestBody HomeLoan loan) {
        log.info("Creating home loan: property={}", loan.getPropertyName());
        loan.setUserId(tenantContext.getCurrentUserId());
        HomeLoan saved = loanRepository.save(loan);
        log.info("Created home loan id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public HomeLoan update(@PathVariable Long id, @RequestBody HomeLoan updated) {
        log.info("Updating home loan id={}", id);
        HomeLoan existing = getById(id);
        existing.setPropertyName(updated.getPropertyName());
        existing.setPropertyAddress(updated.getPropertyAddress());
        existing.setPropertyValue(updated.getPropertyValue());
        existing.setLoanAmount(updated.getLoanAmount());
        existing.setInterestRate(updated.getInterestRate());
        existing.setLoanType(updated.getLoanType());
        existing.setTenureMonths(updated.getTenureMonths());
        existing.setMonthlyEmi(updated.getMonthlyEmi());
        existing.setOutstandingBalance(updated.getOutstandingBalance());
        existing.setTotalPaid(updated.getTotalPaid());
        existing.setTotalInterestPaid(updated.getTotalInterestPaid());
        existing.setStartDate(updated.getStartDate());
        existing.setExpectedEndDate(updated.getExpectedEndDate());
        existing.setBank(updated.getBank());
        existing.setIncludeInNetWorth(updated.getIncludeInNetWorth());
        existing.setNotes(updated.getNotes());
        return loanRepository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Soft-deleting home loan id={}", id);
        HomeLoan loan = getById(id);
        loan.setIsActive(false);
        loanRepository.save(loan);
        return ResponseEntity.noContent().build();
    }

    // ─── Payments ───
    @GetMapping("/{loanId}/payments")
    public List<LoanPayment> getPayments(@PathVariable Long loanId) {
        return paymentRepository.findByLoanIdOrderByPaymentDateDesc(loanId);
    }

    @PostMapping("/{loanId}/payments")
    public ResponseEntity<LoanPayment> createPayment(@PathVariable Long loanId, @RequestBody LoanPayment payment) {
        log.info("Creating loan payment for loanId={}, amount={}", loanId, payment.getAmount());
        HomeLoan loan = getById(loanId);
        payment.setLoan(loan);
        payment.setUserId(tenantContext.getCurrentUserId());
        LoanPayment saved = paymentRepository.save(payment);
        // Update outstanding balance on loan
        if (payment.getBalanceAfter() != null) {
            loan.setOutstandingBalance(payment.getBalanceAfter());
            loanRepository.save(loan);
        }
        log.info("Created loan payment id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/payments/{paymentId}")
    public ResponseEntity<Void> deletePayment(@PathVariable Long paymentId) {
        log.info("Deleting loan payment id={}", paymentId);
        paymentRepository.deleteById(paymentId);
        return ResponseEntity.noContent().build();
    }
}
