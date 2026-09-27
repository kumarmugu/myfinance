package com.myfinance.service.levetf;

import com.myfinance.model.BenchmarkIndex;
import com.myfinance.model.LevEtfInstrument;
import com.myfinance.repository.BenchmarkIndexRepository;
import com.myfinance.repository.LevEtfInstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * OPT-IN scheduled market-data refresh for all users' enabled benchmarks and ETFs. Disabled by default
 * ({@code app.levetf.scheduler.enabled=false}); the bean (and {@link EnableScheduling}) only exist when
 * explicitly turned on, so no timer runs otherwise. There is NO AWS/EventBridge — this is a plain Spring
 * {@code @Scheduled} task. Cron is configurable via {@code app.levetf.scheduler.cron}. Each refresh is
 * idempotent (upsert) and best-effort (never throws), so a provider outage just leaves data unchanged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(name = "app.levetf.scheduler.enabled", havingValue = "true")
public class LevEtfScheduler {

    private final BenchmarkIndexRepository benchmarkRepository;
    private final LevEtfInstrumentRepository instrumentRepository;
    private final LevEtfMarketDataService marketDataService;

    /** Default: 06:30 daily (after most markets close/settle). Override with app.levetf.scheduler.cron. */
    @Scheduled(cron = "${app.levetf.scheduler.cron:0 30 6 * * *}")
    public void refreshAll() {
        log.info("LEV_ETF scheduled refresh starting");
        int ok = 0, fail = 0;
        for (BenchmarkIndex b : benchmarkRepository.findAll()) {
            if (Boolean.FALSE.equals(b.getEnabled()) || b.getUserId() == null) continue;
            try { marketDataService.refreshBenchmarkHistory(b.getUserId(), b.getId(), "5d"); ok++; }
            catch (Exception e) { fail++; log.debug("scheduled benchmark refresh failed id={}: {}", b.getId(), e.toString()); }
        }
        for (LevEtfInstrument e : instrumentRepository.findAll()) {
            if (Boolean.FALSE.equals(e.getEnabled()) || e.getUserId() == null) continue;
            try { marketDataService.refreshEtfHistory(e.getUserId(), e.getId(), "5d"); ok++; }
            catch (Exception ex) { fail++; log.debug("scheduled ETF refresh failed id={}: {}", e.getId(), ex.toString()); }
        }
        log.info("LEV_ETF scheduled refresh done: refreshed={} failed={}", ok, fail);
    }
}
