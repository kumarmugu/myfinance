package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "banks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Bank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String shortName;

    @Builder.Default
    private String country = "Sri Lanka";
}
