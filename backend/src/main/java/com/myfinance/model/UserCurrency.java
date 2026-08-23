package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_currencies", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "code"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserCurrency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false, length = 5)
    private String code; // SGD, USD, EUR, LKR, etc.

    private String name; // "Singapore Dollar", "US Dollar"

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
