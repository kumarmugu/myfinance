package com.myfinance.service.levetf;

import com.myfinance.model.LevEtfAlertHistory;
import com.myfinance.repository.LevEtfAlertHistoryRepository;
import com.myfinance.model.enums.levetf.AlertTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Fires and stores Leveraged ETF alerts. In-app is the PRIMARY channel: every alert is persisted to
 * {@link LevEtfAlertHistory} (deduped on (userId, dedupeKey)) and shown in the web notification surface.
 * Email is OPTIONAL and only attempted when the operator configures SMTP (guarded by
 * {@code app.mail.enabled}); when unset it is a silent no-op so the feature works with in-app only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LevEtfAlertService {

    private final LevEtfAlertHistoryRepository historyRepository;
    private final Optional<LevEtfEmailSender> emailSender; // absent unless mail is configured

    @Value("${app.mail.enabled:false}")
    private boolean emailEnabled;

    public List<LevEtfAlertHistory> list(Long userId) {
        return historyRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long unreadCount(Long userId) {
        return historyRepository.countByUserIdAndReadFlagFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        historyRepository.findById(id)
                .filter(h -> h.getUserId() != null && h.getUserId().equals(userId))
                .ifPresent(h -> { h.setReadFlag(true); historyRepository.save(h); });
    }

    @Transactional
    public void markAllRead(Long userId) {
        for (LevEtfAlertHistory h : historyRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            if (!Boolean.TRUE.equals(h.getReadFlag())) { h.setReadFlag(true); historyRepository.save(h); }
        }
    }

    /**
     * Fire an alert. Deduplicated: if a row with the same {@code dedupeKey} already exists for the user,
     * nothing new is created (the condition is still active). Returns the persisted row, or empty when
     * suppressed by dedupe.
     */
    @Transactional
    public Optional<LevEtfAlertHistory> fire(Long userId, Long strategyId, AlertTrigger trigger,
                                             BigDecimal threshold, String message, String dedupeKey,
                                             boolean wantEmail) {
        if (dedupeKey != null && historyRepository.findByUserIdAndDedupeKey(userId, dedupeKey).isPresent()) {
            return Optional.empty();
        }
        boolean tryEmail = wantEmail && emailEnabled && emailSender.isPresent();
        LevEtfAlertHistory h = LevEtfAlertHistory.builder()
                .userId(userId)
                .strategyId(strategyId)
                .trigger(trigger)
                .threshold(threshold)
                .message(message)
                .dedupeKey(dedupeKey)
                .channelInApp(true)
                .channelEmail(tryEmail)
                .emailDeliveryStatus(tryEmail ? "PENDING" : "SKIPPED_DISABLED")
                .readFlag(false)
                .build();
        LevEtfAlertHistory saved = historyRepository.save(h);

        if (tryEmail) {
            try {
                emailSender.get().send(userId, "MyFinance — Leveraged ETF alert", message);
                saved.setEmailDeliveryStatus("SENT");
            } catch (Exception e) {
                log.warn("Alert email failed for userId={}: {}", userId, e.getMessage());
                saved.setEmailDeliveryStatus("FAILED");
            }
            historyRepository.save(saved);
        }
        log.info("Fired LEV_ETF alert userId={} trigger={} email={}", userId, trigger, tryEmail);
        return Optional.of(saved);
    }

    /**
     * Evaluate a freshly computed snapshot against the user's alert preferences and fire matching alerts.
     * Threshold alerts dedupe per (strategy, trigger, threshold-band) so they don't re-fire every recompute
     * while the condition stays true. Best-effort — never breaks the calculation flow.
     */
    @Transactional
    public void evaluateSnapshot(Long userId, com.myfinance.model.AllocationSnapshot snap,
                                 List<com.myfinance.model.LevEtfAlertPref> prefs) {
        if (snap == null || prefs == null || prefs.isEmpty()) return;
        for (com.myfinance.model.LevEtfAlertPref pref : prefs) {
            if (pref.getStrategyId() != null && !pref.getStrategyId().equals(snap.getStrategyId())) continue;
            if (!Boolean.TRUE.equals(pref.getInAppEnabled())) continue;
            try {
                switch (pref.getTrigger()) {
                    case DRAWDOWN_THRESHOLD -> {
                        if (snap.getDrawdownPercent() != null && pref.getThreshold() != null
                                && snap.getDrawdownPercent().compareTo(pref.getThreshold()) >= 0) {
                            String key = dedupe(snap.getStrategyId(), "DRAWDOWN", pref.getThreshold());
                            fire(userId, snap.getStrategyId(), pref.getTrigger(), pref.getThreshold(),
                                    String.format("Drawdown %.2f%% reached your %.2f%% threshold.",
                                            snap.getDrawdownPercent(), pref.getThreshold()),
                                    key, Boolean.TRUE.equals(pref.getEmailEnabled()));
                        }
                    }
                    case STALE_DATA -> {
                        if (snap.getBlockedReason() != null) {
                            String key = dedupe(snap.getStrategyId(), "STALE", null);
                            fire(userId, snap.getStrategyId(), pref.getTrigger(), null,
                                    "Calculation blocked: " + snap.getBlockedReason(),
                                    key, Boolean.TRUE.equals(pref.getEmailEnabled()));
                        }
                    }
                    default -> { /* other triggers evaluated elsewhere / future work */ }
                }
            } catch (Exception e) {
                log.debug("Alert eval failed for pref {}: {}", pref.getId(), e.toString());
            }
        }
    }

    private String dedupe(Long strategyId, String kind, BigDecimal threshold) {
        // Band the day so the same condition on the same day dedupes but re-arms the next day.
        String day = java.time.LocalDate.now().toString();
        return "s" + strategyId + ":" + kind + ":" + (threshold == null ? "" : threshold.toPlainString()) + ":" + day;
    }
}
