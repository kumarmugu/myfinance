package com.myfinance.service.levetf;

import com.myfinance.model.BenchmarkIndex;
import com.myfinance.model.LevEtfInstrument;
import com.myfinance.model.MarketDataBar;
import com.myfinance.model.enums.levetf.DataQuality;
import com.myfinance.model.enums.levetf.InstrumentType;
import com.myfinance.repository.BenchmarkIndexRepository;
import com.myfinance.repository.LevEtfInstrumentRepository;
import com.myfinance.repository.MarketDataBarRepository;
import com.myfinance.service.PriceFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persists and refreshes {@link MarketDataBar} history for benchmarks and leveraged ETFs, per user.
 * All fetches are best-effort via {@link PriceFetchService} (never throws); the store is the source of
 * truth for drawdown, reference-high, and backtests so the engine reads OK-quality closes only.
 *
 * <p>Refresh is idempotent: bars are upserted on (userId, instrumentType, instrumentId, date). A bar's
 * {@link DataQuality} is stamped {@code STALE} when the latest bar is older than
 * {@code app.levetf.stale-after-hours}; missing symbols yield no bars (never zero-substituted).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LevEtfMarketDataService {

    private final MarketDataBarRepository barRepository;
    private final BenchmarkIndexRepository benchmarkRepository;
    private final LevEtfInstrumentRepository instrumentRepository;
    private final PriceFetchService priceFetchService;

    @Value("${app.levetf.stale-after-hours:36}")
    private long staleAfterHours;

    /** Outcome of a refresh call, surfaced to the API. */
    public record RefreshResult(int barsInserted, int barsUpdated, boolean providerEnabled, String message) {}

    // ─────────────────────────── history refresh ───────────────────────────

    /** Refresh a benchmark's daily history for the user, upserting bars. Best-effort. */
    public RefreshResult refreshBenchmarkHistory(Long userId, Long benchmarkId, String range) {
        BenchmarkIndex b = benchmarkRepository.findById(benchmarkId)
                .filter(x -> x.getUserId() != null && x.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Benchmark not found"));
        return refreshHistory(userId, InstrumentType.BENCHMARK, benchmarkId, b.getSymbol(),
                b.getExchange(), b.getCurrency(), range);
    }

    /** Refresh an ETF's daily history for the user, upserting bars. Best-effort. */
    public RefreshResult refreshEtfHistory(Long userId, Long instrumentId, String range) {
        LevEtfInstrument e = instrumentRepository.findById(instrumentId)
                .filter(x -> x.getUserId() != null && x.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("ETF instrument not found"));
        return refreshHistory(userId, InstrumentType.ETF, instrumentId, e.getSymbol(),
                e.getExchange(), e.getTradingCurrency(), range);
    }

    private RefreshResult refreshHistory(Long userId, InstrumentType type, Long instrumentId,
                                         String symbol, String exchange, String currency, String range) {
        if (!priceFetchService.isEnabled()) {
            return new RefreshResult(0, 0, false,
                    "Price provider is disabled — enter history manually or enable app.price.enabled.");
        }
        List<PriceFetchService.DailyBar> bars = priceFetchService.fetchDailyHistory(symbol, exchange, range);
        if (bars.isEmpty()) {
            return new RefreshResult(0, 0, true,
                    "No data returned for " + symbol + " — the symbol may be unquoted on the provider. "
                            + "You can enter bars manually.");
        }
        int inserted = 0, updated = 0;
        LocalDateTime now = LocalDateTime.now();
        for (PriceFetchService.DailyBar bar : bars) {
            Optional<MarketDataBar> existing = barRepository
                    .findByUserIdAndInstrumentTypeAndInstrumentIdAndDate(userId, type, instrumentId, bar.date());
            MarketDataBar row = existing.orElseGet(MarketDataBar::new);
            boolean isNew = existing.isEmpty();
            row.setUserId(userId);
            row.setInstrumentType(type);
            row.setInstrumentId(instrumentId);
            row.setDate(bar.date());
            row.setOpen(bar.open());
            row.setHigh(bar.high());
            row.setLow(bar.low());
            row.setClose(bar.close());
            row.setAdjustedClose(bar.adjClose());
            row.setVolume(bar.volume());
            row.setCurrency(currency);
            row.setProvider(priceFetchService.getProvider());
            row.setSourceTimestamp(now);
            row.setIngestionTimestamp(now);
            row.setDataQuality(bar.close() != null && bar.close().signum() > 0 ? DataQuality.OK : DataQuality.INVALID);
            barRepository.save(row);
            if (isNew) inserted++; else updated++;
        }
        log.info("LevEtf market-data refresh userId={} {}#{} symbol={} inserted={} updated={}",
                userId, type, instrumentId, symbol, inserted, updated);
        return new RefreshResult(inserted, updated, true,
                "Imported " + inserted + " new and updated " + updated + " bars for " + symbol + ".");
    }

    /** Upsert a single manually-entered bar (used when the provider doesn't cover a symbol, e.g. an index). */
    public MarketDataBar upsertManualBar(Long userId, InstrumentType type, Long instrumentId,
                                         LocalDate date, BigDecimal close, String currency) {
        if (close == null || close.signum() <= 0) throw new RuntimeException("Close must be positive");
        MarketDataBar row = barRepository
                .findByUserIdAndInstrumentTypeAndInstrumentIdAndDate(userId, type, instrumentId, date)
                .orElseGet(MarketDataBar::new);
        row.setUserId(userId);
        row.setInstrumentType(type);
        row.setInstrumentId(instrumentId);
        row.setDate(date);
        row.setClose(close);
        row.setCurrency(currency);
        row.setProvider("MANUAL");
        row.setSourceTimestamp(LocalDateTime.now());
        row.setIngestionTimestamp(LocalDateTime.now());
        row.setDataQuality(DataQuality.OK);
        return barRepository.save(row);
    }

    // ─────────────────────────── reads ───────────────────────────

    public List<MarketDataBar> history(Long userId, InstrumentType type, Long instrumentId) {
        return barRepository.findByUserIdAndInstrumentTypeAndInstrumentIdOrderByDateAsc(userId, type, instrumentId);
    }

    /**
     * The latest stored bar with a computed freshness quality: OK if within the stale window, otherwise
     * STALE. Empty when there is no history (caller treats as MISSING and blocks the calculation).
     */
    public Optional<MarketDataBar> latestBar(Long userId, InstrumentType type, Long instrumentId) {
        Optional<MarketDataBar> latest = barRepository
                .findFirstByUserIdAndInstrumentTypeAndInstrumentIdOrderByDateDesc(userId, type, instrumentId);
        latest.ifPresent(bar -> {
            if (bar.getSourceTimestamp() != null) {
                Duration age = Duration.between(bar.getSourceTimestamp(), LocalDateTime.now());
                if (bar.getDataQuality() == DataQuality.OK && age.toHours() > staleAfterHours) {
                    bar.setDataQuality(DataQuality.STALE);
                }
            }
        });
        return latest;
    }

    /** Convert stored bars to the engine's dated closes (ascending), preserving quality. */
    public List<AllocationCalculationService.HistoricalClose> toHistoricalCloses(List<MarketDataBar> bars) {
        return bars.stream()
                .map(b -> new AllocationCalculationService.HistoricalClose(
                        b.getDate(), b.getClose(),
                        b.getDataQuality() == null ? DataQuality.OK : b.getDataQuality()))
                .toList();
    }
}
