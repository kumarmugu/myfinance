package com.myfinance.controller;

import com.myfinance.model.Account;
import com.myfinance.model.AppUser;
import com.myfinance.model.Owner;
import com.myfinance.model.enums.Broker;
import com.myfinance.repository.AccountRepository;
import com.myfinance.repository.OwnerRepository;
import com.myfinance.security.TenantContext;
import com.myfinance.service.BrokerCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BrokerCredentialController}: the feature gate (403 without broker-sync, 401
 * when unauthenticated), tenant checks on save (400 for another user's account/owner), the masked
 * GET response shape, and 503 when encryption isn't configured. No Spring context — the controller is
 * constructed with mocked collaborators.
 */
class BrokerCredentialControllerTest {

    private BrokerCredentialService service;
    private TenantContext tenantContext;
    private AccountRepository accountRepository;
    private OwnerRepository ownerRepository;
    private BrokerCredentialController controller;

    private AppUser user;

    @BeforeEach
    void setUp() {
        service = mock(BrokerCredentialService.class);
        tenantContext = mock(TenantContext.class);
        accountRepository = mock(AccountRepository.class);
        ownerRepository = mock(OwnerRepository.class);
        controller = new BrokerCredentialController(service, tenantContext, accountRepository, ownerRepository);

        user = new AppUser();
        user.setId(1L);
        user.setUsername("mugu");
        user.setEnabledFeatures("BROKER_SYNC");
    }

    private BrokerCredentialController.SaveRequest saveReq() {
        return new BrokerCredentialController.SaveRequest("IBKR", 10L, 100L, "q1", null, "tok", null);
    }

    @Test
    void listReturnsMaskedStatusAndEncryptionFlag() {
        when(tenantContext.getCurrentUser()).thenReturn(user);
        when(service.encryptionEnabled()).thenReturn(true);
        when(service.listStatuses(1L)).thenReturn(java.util.List.of());

        Map<String, Object> body = controller.list();
        assertEquals(true, body.get("encryptionEnabled"));
        assertNotNull(body.get("credentials"));
        verify(service).listStatuses(1L);
    }

    @Test
    void unauthenticatedIs401() {
        when(tenantContext.getCurrentUser()).thenReturn(null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.list());
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void withoutBrokerSyncFeatureIs403() {
        user.setEnabledFeatures("EXPENSES,TAX"); // broker sync not enabled
        when(tenantContext.getCurrentUser()).thenReturn(user);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.list());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void saveIs503WhenEncryptionDisabled() {
        when(tenantContext.getCurrentUser()).thenReturn(user);
        when(service.encryptionEnabled()).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.save(saveReq()));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    void saveRejectsAnotherUsersAccount() {
        when(tenantContext.getCurrentUser()).thenReturn(user);
        when(service.encryptionEnabled()).thenReturn(true);
        Account otherUsersAccount = new Account();
        otherUsersAccount.setId(100L);
        otherUsersAccount.setUserId(999L); // belongs to a different tenant
        when(accountRepository.findById(100L)).thenReturn(Optional.of(otherUsersAccount));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.save(saveReq()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(service, never()).save(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void saveSucceedsForOwnAccountAndOwner() {
        when(tenantContext.getCurrentUser()).thenReturn(user);
        when(service.encryptionEnabled()).thenReturn(true);
        Account acc = new Account(); acc.setId(100L); acc.setUserId(1L);
        Owner own = new Owner(); own.setId(10L); own.setUserId(1L);
        when(accountRepository.findById(100L)).thenReturn(Optional.of(acc));
        when(ownerRepository.findById(10L)).thenReturn(Optional.of(own));
        when(service.save(eq(1L), eq(10L), eq(100L), eq(Broker.IBKR), eq("q1"), isNull(), eq("tok"), isNull()))
                .thenReturn(new BrokerCredentialService.CredentialStatus(5L, Broker.IBKR, 10L, 100L, "q1", true, false, null));

        var status = controller.save(saveReq());
        assertEquals(Broker.IBKR, status.broker());
        assertTrue(status.secret1Set());
        verify(service).save(1L, 10L, 100L, Broker.IBKR, "q1", null, "tok", null);
    }

    @Test
    void deleteDelegatesToServiceForTheCurrentUser() {
        when(tenantContext.getCurrentUser()).thenReturn(user);
        controller.delete("TIGER", 200L);
        verify(service).delete(1L, 200L, Broker.TIGER);
    }

    @Test
    void unknownBrokerIs400OnDelete() {
        when(tenantContext.getCurrentUser()).thenReturn(user);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.delete("MOOMOO", 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
