package com.myfinance.saas.signup.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Self-service signup input. Collects only what is required: name, email, password, plan
 * (optional), and terms acceptance.
 */
@Data
public class SignupRequest {

    @NotBlank
    @Size(max = 120)
    private String fullName;

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @NotBlank
    @Size(max = 128)
    private String password;

    /** Optional plan code; defaults to the free trial plan when omitted. */
    private String planCode;

    @AssertTrue(message = "You must accept the terms to sign up")
    private boolean acceptTerms;
}
