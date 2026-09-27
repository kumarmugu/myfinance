package com.myfinance.repository;

import com.myfinance.model.MarketDataBar;
import com.myfinance.model.enums.levetf.InstrumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketDataBarRepository extends JpaRepository<MarketDataBar, Long> {

    List<MarketDataBar> findByUserIdAndInstrumentTypeAndInstrumentIdOrderByDateAsc(
            Long userId, InstrumentType instrumentType, Long instrumentId);

    Optional<MarketDataBar> findByUserIdAndInstrumentTypeAndInstrumentIdAndDate(
            Long userId, InstrumentType instrumentType, Long instrumentId, LocalDate date);

    Optional<MarketDataBar> findFirstByUserIdAndInstrumentTypeAndInstrumentIdOrderByDateDesc(
            Long userId, InstrumentType instrumentType, Long instrumentId);
}
