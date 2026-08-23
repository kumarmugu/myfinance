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
    public List<SoldPosition> getByUser(Long userId) { return soldPositionRepository.findByUserIdOrderBySoldDateDesc(userId); }
    public List<SoldPosition> getByOwner(Long ownerId) { return soldPositionRepository.findByOwnerIdOrderBySoldDateDesc(ownerId); }
    public List<SoldPosition> getByAccount(Long accountId) { return soldPositionRepository.findByAccountIdOrderBySoldDateDesc(accountId); }
    public List<SoldPosition> getShortTerm() { return soldPositionRepository.findShortTermTrades(); }
    public List<SoldPosition> getShortTermForUser(Long userId) { return soldPositionRepository.findShortTermTradesByUser(userId); }
    public SoldPosition create(SoldPosition sp) { return soldPositionRepository.save(sp); }
    public void delete(Long id) { soldPositionRepository.deleteById(id); }
}
