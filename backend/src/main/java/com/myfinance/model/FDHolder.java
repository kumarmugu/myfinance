package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "fd_holders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FDHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String relationship;

    @Builder.Default
    private Boolean isSeniorCitizen = false;

    private String notes;
}
