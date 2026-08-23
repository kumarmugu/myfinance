package com.myfinance.service;

import com.myfinance.model.AuditLog;
import com.myfinance.model.AppUser;
import com.myfinance.repository.AuditLogRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final TenantContext tenantContext;

    /**
     * Log an audit event for the current authenticated user.
     */
    public void log(String action, String entity, Long entityId, String details) {
        try {
            AppUser user = tenantContext.getCurrentUser();
            AuditLog entry = AuditLog.builder()
                    .userId(user != null ? user.getId() : null)
                    .username(user != null ? user.getUsername() : "system")
                    .action(action)
                    .entity(entity)
                    .entityId(entityId)
                    .details(details)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit logging should never break the main flow
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    /**
     * Query audit logs with filters (admin only).
     */
    public Page<AuditLog> getAuditLogs(Long userId, String action, String entity,
                                        LocalDateTime from, LocalDateTime to,
                                        int page, int size) {
        return auditLogRepository.findFiltered(userId, action, entity, from, to,
                PageRequest.of(page, size));
    }
}
