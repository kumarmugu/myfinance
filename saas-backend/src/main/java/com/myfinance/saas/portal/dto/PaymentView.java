package com.myfinance.saas.portal.dto;

import com.myfinance.saas.domain.PaymentTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Customer-facing payment history entry. Only non-sensitive fields — never card data.
 */
@Data
@Builder
@AllArgsConstructor
public class PaymentView {
    private Long id;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String method;
    private String receiptUrl;
    private String failureReason;
    private LocalDateTime date;

    public static PaymentView from(PaymentTransaction tx) {
        return PaymentView.builder()
                .id(tx.getId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .status(tx.getStatus() != null ? tx.getStatus().name() : null)
                .method(tx.getMethod())
                .receiptUrl(tx.getReceiptUrl())
                .failureReason(tx.getFailureReason())
                .date(tx.getCreatedAt())
                .build();
    }
}
