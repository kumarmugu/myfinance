package com.myfinance.saas.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long customerId;
    private String email;
    private String fullName;
    private boolean emailVerified;
}
