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

    /** Dividends belonging to the current user, scoped to one owner AND one account. */
    private List<Dividend> scoped(Long userId, Long ownerId, Long accountId) {
        return dividendRepository.findByUserIdOrderByReceivedDateDesc(userId).stream()
                .filter(d -> d.getOwner() != null && ownerId.equals(d.getOwner().getId()))
                .filter(d -> d.getAccount() != null && accountId.equals(d.getAccount().getId()))
                .toList();
    }

    /** How many dividends a bulk delete for this owner+account would remove (preview). */
    public long countForOwnerAccount(Long userId, Long ownerId, Long accountId) {
        return scoped(userId, ownerId, accountId).size();
    }

    /** Bulk-delete the current user's dividends for one owner+account. Returns the number removed. */
    @org.springframework.transaction.annotation.Transactional
    public int deleteForOwnerAccount(Long userId, Long ownerId, Long accountId) {
        List<Dividend> toDelete = scoped(userId, ownerId, accountId);
        dividendRepository.deleteAll(toDelete);
        return toDelete.size();
    }
    public List<Object[]> getSummaryByYear() { return dividendRepository.sumByYear(); }
    public List<Object[]> getSummaryByYearForUser(Long userId) { return dividendRepository.sumByYearForUser(userId); }
}
