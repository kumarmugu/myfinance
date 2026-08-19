package com.myfinance.model;

import com.myfinance.model.enums.AssetType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "allocation_targets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AllocationTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetType assetType;

    @Column(nullable = false)
    private BigDecimal targetPercentage;

    private BigDecimal targetAmount;
}
