package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "net_worth_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NetWorthConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String assetType; // e.g. INDEX_FUND, CRYPTO, SAVINGS, etc.

    @Column(nullable = false)
    @Builder.Default
    private Boolean includeInNetWorth = true;

    private String label; // display name
}
