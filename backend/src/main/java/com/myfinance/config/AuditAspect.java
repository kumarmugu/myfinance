package com.myfinance.config;

import com.myfinance.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;

/**
 * AOP aspect that automatically logs CREATE, UPDATE, and DELETE operations
 * performed through REST controllers.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService auditService;

    /**
     * Intercept all POST (create) methods in controllers.
     */
    @AfterReturning(
        pointcut = "execution(* com.myfinance.controller..*(..)) && @annotation(postMapping)",
        returning = "result"
    )
    public void auditCreate(JoinPoint joinPoint, PostMapping postMapping, Object result) {
        logAudit(joinPoint, "CREATE", result);
    }

    /**
     * Intercept all PUT (update) methods in controllers.
     */
    @AfterReturning(
        pointcut = "execution(* com.myfinance.controller..*(..)) && @annotation(putMapping)",
        returning = "result"
    )
    public void auditUpdate(JoinPoint joinPoint, PutMapping putMapping, Object result) {
        logAudit(joinPoint, "UPDATE", result);
    }

    /**
     * Intercept all PATCH (partial update) methods in controllers.
     */
    @AfterReturning(
        pointcut = "execution(* com.myfinance.controller..*(..)) && @annotation(patchMapping)",
        returning = "result"
    )
    public void auditPatch(JoinPoint joinPoint, PatchMapping patchMapping, Object result) {
        logAudit(joinPoint, "UPDATE", result);
    }

    /**
     * Intercept all DELETE methods in controllers.
     */
    @AfterReturning(
        pointcut = "execution(* com.myfinance.controller..*(..)) && @annotation(deleteMapping)",
        returning = "result"
    )
    public void auditDelete(JoinPoint joinPoint, DeleteMapping deleteMapping, Object result) {
        logAudit(joinPoint, "DELETE", result);
    }

    private void logAudit(JoinPoint joinPoint, String action, Object result) {
        try {
            String controllerName = joinPoint.getTarget().getClass().getSimpleName();
            String entity = controllerName.replace("Controller", "");
            String methodName = joinPoint.getSignature().getName();

            // Skip audit logging for the audit controller itself and auth endpoints
            if ("Audit".equals(entity) || "Auth".equals(entity)) {
                return;
            }

            // Try to extract entity ID from the result or method arguments
            Long entityId = extractEntityId(joinPoint, result);

            String details = String.format("%s.%s", entity, methodName);

            auditService.log(action, entity, entityId, details);
        } catch (Exception e) {
            log.debug("Audit logging failed for {}: {}", joinPoint.getSignature().getName(), e.getMessage());
        }
    }

    private Long extractEntityId(JoinPoint joinPoint, Object result) {
        // Try to get ID from the result object
        Long id = extractIdFromObject(result);
        if (id != null) return id;

        // Try to get ID from method arguments (common for DELETE /entity/{id})
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Long) {
                return (Long) arg;
            }
        }
        return null;
    }

    private Long extractIdFromObject(Object obj) {
        if (obj == null) return null;

        // Unwrap ResponseEntity
        if (obj instanceof ResponseEntity<?> re) {
            obj = re.getBody();
            if (obj == null) return null;
        }

        // Try to call getId() via reflection
        try {
            Method getId = obj.getClass().getMethod("getId");
            Object id = getId.invoke(obj);
            if (id instanceof Long) return (Long) id;
            if (id instanceof Number) return ((Number) id).longValue();
        } catch (Exception ignored) {
            // No getId() method, that's fine
        }
        return null;
    }
}
