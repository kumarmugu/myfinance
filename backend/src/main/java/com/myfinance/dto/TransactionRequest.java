package com.myfinance.dto;

import com.myfinance.model.enums.InvestmentPurpose;
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
    private Long ownerId;
    @NotNull
    private TransactionType transactionType;
    @NotNull @Positive
    private BigDecimal quantity;
    @NotNull @Positive
    private BigDecimal pricePerUnit;
    private BigDecimal fees;
    /** Currency of the fee; may differ from the trade currency (e.g. SGD fee on a USD trade). */
    private String feeCurrency;
    /** FX rate trade-currency → account-currency at purchase, for cross-currency cost basis. */
    private BigDecimal fxRateToBase;
    private String currency;
    private LocalDate transactionDate;
    private String notes;
    private InvestmentPurpose purpose;
}
