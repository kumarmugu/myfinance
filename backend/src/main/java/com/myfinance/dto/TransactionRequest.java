package com.myfinance.dto;

import com.myfinance.model.enums.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TransactionRequest {
    @NotNull
    private Long assetId;

    @NotNull
    private Long accountId;

    @NotNull
    private TransactionType transactionType;

    @NotNull
    @Positive
    private BigDecimal quantity;

    @NotNull
    @Positive
    private BigDecimal pricePerUnit;

    private BigDecimal fees;

    private LocalDate transactionDate;

    private String notes;
}
