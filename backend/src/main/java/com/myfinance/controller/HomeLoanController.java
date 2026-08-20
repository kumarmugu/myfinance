package com.myfinance.controller;

import com.myfinance.model.HomeLoan;
import com.myfinance.model.LoanPayment;
import com.myfinance.repository.HomeLoanRepository;
import com.myfinance.repository.LoanPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/home-loans")
@RequiredArgsConstructor
public class HomeLoanController {
    private final HomeLoanRepository loanRepository;
    private final LoanPaymentRepository paymentRepository;

    @GetMapping
    public List<HomeLoan> getAll(@RequestParam(required = false) Long ownerId) {
        if (ownerId != null) return loanRepository.findByOwnerIdOrderByPropertyNameAsc(ownerId);
        return loanRepository.findByIsActiveTrueOrderByPropertyNameAsc();
    }

    @GetMapping("/{id}")
    public HomeLoan getById(@PathVariable Long id) {
        return loanRepository.findById(id).orElseThrow(() -> new RuntimeException("Loan not found"));
    }

    @PostMapping
    public ResponseEntity<HomeLoan> create(@RequestBody HomeLoan loan) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanRepository.save(loan));
    }

    @PutMapping("/{id}")
    public HomeLoan update(@PathVariable Long id, @RequestBody HomeLoan updated) {
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
        HomeLoan loan = getById(loanId);
        payment.setLoan(loan);
        LoanPayment saved = paymentRepository.save(payment);
        // Update outstanding balance on loan
        if (payment.getBalanceAfter() != null) {
            loan.setOutstandingBalance(payment.getBalanceAfter());
            loanRepository.save(loan);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/payments/{paymentId}")
    public ResponseEntity<Void> deletePayment(@PathVariable Long paymentId) {
        paymentRepository.deleteById(paymentId);
        return ResponseEntity.noContent().build();
    }
}
