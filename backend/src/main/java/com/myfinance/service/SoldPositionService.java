package com.myfinance.service;

import com.myfinance.model.SoldPosition;
import com.myfinance.repository.SoldPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SoldPositionService {
    private final SoldPositionRepository soldPositionRepository;

    public List<SoldPosition> getAll() { return soldPositionRepository.findAllByOrderBySoldDateDesc(); }
    public List<SoldPosition> getByOwner(Long ownerId) { return soldPositionRepository.findByOwnerIdOrderBySoldDateDesc(ownerId); }
    public List<SoldPosition> getByAccount(Long accountId) { return soldPositionRepository.findByAccountIdOrderBySoldDateDesc(accountId); }
    public List<SoldPosition> getShortTerm() { return soldPositionRepository.findByIsShortTermTrueOrderBySoldDateDesc(); }
    public SoldPosition create(SoldPosition sp) { return soldPositionRepository.save(sp); }
    public void delete(Long id) { soldPositionRepository.deleteById(id); }
}
