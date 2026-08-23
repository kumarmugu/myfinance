package com.myfinance.service;

import com.myfinance.model.Dividend;
import com.myfinance.repository.DividendRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DividendService {
    private final DividendRepository dividendRepository;

    public List<Dividend> getAll() { return dividendRepository.findAllByOrderByReceivedDateDesc(); }
    public List<Dividend> getByUser(Long userId) { return dividendRepository.findByUserIdOrderByReceivedDateDesc(userId); }
    public List<Dividend> getByOwner(Long ownerId) { return dividendRepository.findByOwnerIdOrderByReceivedDateDesc(ownerId); }
    public List<Dividend> getByAccount(Long accountId) { return dividendRepository.findByAccountIdOrderByReceivedDateDesc(accountId); }
    public List<Dividend> getByYear(Integer year) { return dividendRepository.findByYear(year); }
    public Dividend create(Dividend dividend) { return dividendRepository.save(dividend); }
    public void delete(Long id) { dividendRepository.deleteById(id); }
    public List<Object[]> getSummaryByYear() { return dividendRepository.sumByYear(); }
    public List<Object[]> getSummaryByYearForUser(Long userId) { return dividendRepository.sumByYearForUser(userId); }
}
