package com.myfinance.service.levetf;

import com.myfinance.model.AllocationSnapshot;
import com.myfinance.model.LevEtfStrategy;
import com.myfinance.model.RebalancePlan;
import com.myfinance.model.enums.levetf.DataQuality;
import com.myfinance.model.enums.levetf.RebalanceAction;
import com.myfinance.model.enums.levetf.RebalancePlanStatus;
import com.myfinance.repository.RebalancePlanRepository;
import com.myfinance.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages rebalance proposals and their decision trail. Strictly advisory: generating a plan never
 * places an order, approval requires an explicit user action, and execution is recorded manually.
 * Each plan pins the {@code allocationSnapshotId} + {@code ruleVersion} it was computed under, so later
 * rule/strategy edits never change a historical plan. Ownership is verified on every mutation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RebalancePlanService {

    private final RebalancePlanRepository planRepository;
    private final LevEtfPlannerService plannerService;
    private final AuditService auditService;

    public List<RebalancePlan> list(Long userId, Long strategyId) {
        return strategyId == null
                ? planRepository.findByUserIdOrderByCreatedAtDesc(userId)
                : planRepository.findByUserIdAndStrategyIdOrderByCreatedAtDesc(userId, strategyId);
    }

    private RebalancePlan requireOwned(Long userId, Long planId) {
        return planRepository.findById(planId)
                .filter(p -> p.getUserId() != null && p.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Rebalance plan not found"));
    }

    /**
     * Generate a fresh proposal for a strategy from a newly computed allocation snapshot. Refuses to
     * create a proposal when the snapshot is blocked (stale/missing/invalid data) — no fabricated trade.
     */
    @Transactional
    public RebalancePlan generate(Long userId, Long strategyId) {
        LevEtfStrategy strategy = plannerService.requireStrategy(userId, strategyId);
        AllocationSnapshot snap = plannerService.computeAndSnapshot(userId, strategyId);

        if (snap.getBlockedReason() != null || snap.getDataQuality() != DataQuality.OK
                || snap.getRebalanceDifferenceBase() == null || snap.getTargetAllocationPercent() == null) {
            throw new RuntimeException(snap.getBlockedReason() != null
                    ? snap.getBlockedReason()
                    : "Data is not fresh/complete enough to generate a rebalance proposal. Refresh prices and retry.");
        }

        BigDecimal diff = snap.getRebalanceDifferenceBase();
        RebalanceAction action = diff.signum() == 0 ? RebalanceAction.NO_ACTION
                : diff.signum() > 0 ? RebalanceAction.BUY : RebalanceAction.REDUCE;

        RebalancePlan plan = RebalancePlan.builder()
                .userId(userId)
                .strategyId(strategyId)
                .allocationSnapshotId(snap.getId())
                .ruleVersion(snap.getRuleVersion())
                .action(action)
                .estimatedAmount(diff.abs())
                .currency(strategy.getTradingCurrency())
                .reason(String.format("Target %.2f%% vs actual %s%% (drawdown %.2f%%). Difference %s (base).",
                        snap.getTargetAllocationPercent(),
                        snap.getActualAllocationPercent() == null ? "?" : snap.getActualAllocationPercent().toPlainString(),
                        snap.getDrawdownPercent(), diff.toPlainString()))
                .status(action == RebalanceAction.NO_ACTION
                        ? RebalancePlanStatus.DRAFT : RebalancePlanStatus.PENDING_REVIEW)
                .build();
        RebalancePlan saved = planRepository.save(plan);
        auditService.log("CREATE", "RebalancePlan", saved.getId(),
                "Generated " + action + " proposal for strategy " + strategyId);
        log.info("Generated rebalance plan id={} action={} userId={}", saved.getId(), action, userId);
        return saved;
    }

    /** Explicit user approval. Does NOT place any order. */
    @Transactional
    public RebalancePlan approve(Long userId, Long planId) {
        RebalancePlan plan = requireOwned(userId, planId);
        if (plan.getStatus() != RebalancePlanStatus.PENDING_REVIEW) {
            throw new RuntimeException("Only a plan pending review can be approved.");
        }
        plan.setStatus(RebalancePlanStatus.APPROVED);
        plan.setApprovalTimestamp(LocalDateTime.now());
        RebalancePlan saved = planRepository.save(plan);
        auditService.log("UPDATE", "RebalancePlan", planId, "Approved rebalance plan (advisory only, no order placed)");
        return saved;
    }

    @Transactional
    public RebalancePlan reject(Long userId, Long planId, String notes) {
        RebalancePlan plan = requireOwned(userId, planId);
        plan.setStatus(RebalancePlanStatus.CANCELLED);
        if (notes != null) plan.setNotes(notes);
        RebalancePlan saved = planRepository.save(plan);
        auditService.log("UPDATE", "RebalancePlan", planId, "Rejected/cancelled rebalance plan");
        return saved;
    }

    /** Record a manual execution (full or partial). Never talks to a broker. */
    @Transactional
    public RebalancePlan recordExecution(Long userId, Long planId, BigDecimal executedQuantity,
                                         BigDecimal executedPrice, BigDecimal fees, String brokerReference,
                                         boolean partial, String notes) {
        RebalancePlan plan = requireOwned(userId, planId);
        if (plan.getStatus() != RebalancePlanStatus.APPROVED
                && plan.getStatus() != RebalancePlanStatus.PARTIALLY_EXECUTED) {
            throw new RuntimeException("Only an approved plan can record an execution.");
        }
        plan.setExecutedQuantity(executedQuantity);
        plan.setExecutedPrice(executedPrice);
        plan.setExecutedFees(fees);
        plan.setBrokerReference(brokerReference);
        plan.setExecutionTimestamp(LocalDateTime.now());
        if (notes != null) plan.setNotes(notes);
        plan.setStatus(partial ? RebalancePlanStatus.PARTIALLY_EXECUTED : RebalancePlanStatus.EXECUTED);
        RebalancePlan saved = planRepository.save(plan);
        auditService.log("UPDATE", "RebalancePlan", planId,
                "Recorded " + (partial ? "partial " : "") + "execution qty=" + executedQuantity);
        return saved;
    }

    @Transactional
    public void cancel(Long userId, Long planId) {
        RebalancePlan plan = requireOwned(userId, planId);
        plan.setStatus(RebalancePlanStatus.CANCELLED);
        planRepository.save(plan);
        auditService.log("UPDATE", "RebalancePlan", planId, "Cancelled rebalance plan");
    }
}
