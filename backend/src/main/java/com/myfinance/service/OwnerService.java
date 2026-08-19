package com.myfinance.service;

import com.myfinance.model.Owner;
import com.myfinance.repository.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerService {
    private final OwnerRepository ownerRepository;

    public List<Owner> getAllOwners() { return ownerRepository.findByIsActiveTrue(); }
    public Owner getById(Long id) { return ownerRepository.findById(id).orElseThrow(() -> new RuntimeException("Owner not found: " + id)); }
    public Owner create(Owner owner) { return ownerRepository.save(owner); }
}
