package com.myfinance.saas.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SaasAdminRepository extends JpaRepository<SaasAdmin, Long> {
    Optional<SaasAdmin> findByEmail(String email);
}
