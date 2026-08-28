package com.myfinance.saas.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Portal request to start a checkout for a paid plan. The customer is taken from the JWT,
 * never from this body (object-level authorization).
 */
@Data
public class CheckoutRequestDto {

    @NotBlank
    private String planCode;

    /** "card" or "paynow"; defaults to card if omitted. */
    private String method = "card";
}
