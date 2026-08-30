package com.myfinance.repository;

import com.myfinance.model.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {
    List<Property> findByUserIdOrderByPropertyNameAsc(Long userId);
    List<Property> findByUserIdAndStatus(Long userId, String status);
    List<Property> findByOwnerIdOrderByPropertyNameAsc(Long ownerId);
}
