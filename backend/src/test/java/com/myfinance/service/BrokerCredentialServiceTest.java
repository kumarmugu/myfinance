package com.myfinance.service;

import com.myfinance.model.BrokerCredential;
import com.myfinance.model.enums.Broker;
import com.myfinance.repository.BrokerCredentialRepository;
import com.myfinance.security.CredentialCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Tests {@link BrokerCredentialService}: secrets are encrypted on write; the masked status view never
 * exposes secret values; a blank secret on update keeps the previously-stored one; and decryptFor
 * returns the original plaintext for the sync services.
 *
 * <p>Uses a real {@link CredentialCipher} (with a test key) so we assert genuine encryption, and an
 * in-memory fake over a mocked repository so save/find behaves like the DB.
 */
class BrokerCredentialServiceTest {

    private BrokerCredentialRepository repository;
    private BrokerCredentialService service;
    private final Map<Long, BrokerCredential> store = new HashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    private static String key256() {
        byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) raw[i] = (byte) (i + 7);
        return Base64.getEncoder().encodeToString(raw);
    }

    @BeforeEach
    void setUp() {
        repository = mock(BrokerCredentialRepository.class);
        CredentialCipher cipher = new CredentialCipher(key256());
        service = new BrokerCredentialService(repository, cipher);

        // Fake persistence: save assigns an id and stores; finders read from the map.
        when(repository.save(any(BrokerCredential.class))).thenAnswer(inv -> {
            BrokerCredential c = inv.getArgument(0);
            if (c.getId() == null) c.setId(seq.getAndIncrement());
            store.put(c.getId(), c);
            return c;
        });
        when(repository.findByUserIdAndAccountIdAndBroker(anyLong(), anyLong(), any())).thenAnswer(inv ->
            store.values().stream()
                .filter(c -> c.getUserId().equals(inv.getArgument(0))
                        && c.getAccountId().equals(inv.getArgument(1))
                        && c.getBroker().equals(inv.getArgument(2)))
                .findFirst());
        when(repository.findByUserId(anyLong())).thenAnswer(inv ->
            store.values().stream().filter(c -> c.getUserId().equals(inv.getArgument(0))).toList());
    }

    @Test
    void saveEncryptsSecretAndStatusNeverExposesIt() {
        var status = service.save(1L, 10L, 100L, Broker.IBKR, "queryId-123", null, "flex-secret-token", null);

        // The stored ciphertext must not be the plaintext.
        BrokerCredential stored = store.values().iterator().next();
        assertNotNull(stored.getSecret1());
        assertNotEquals("flex-secret-token", stored.getSecret1(), "secret is stored encrypted, not in plaintext");

        // The masked status exposes only that a secret is set — never the value.
        assertEquals("queryId-123", status.meta1(), "non-secret identifier is visible");
        assertTrue(status.secret1Set());
        assertFalse(status.secret2Set());
        // The CredentialStatus record has no field carrying a secret value.
        assertFalse(status.toString().contains("flex-secret-token"), "status must not leak the secret");
    }

    @Test
    void blankSecretOnUpdateKeepsTheExistingOne() {
        service.save(1L, 10L, 100L, Broker.IBKR, "queryId-123", null, "original-token", null);
        String encAfterCreate = store.values().iterator().next().getSecret1();

        // Update only the Query ID; leave the secret blank.
        service.save(1L, 10L, 100L, Broker.IBKR, "queryId-999", null, "  ", null);

        BrokerCredential stored = store.values().iterator().next();
        assertEquals("queryId-999", stored.getMeta1(), "metadata updated");
        assertEquals(encAfterCreate, stored.getSecret1(), "blank secret leaves the stored ciphertext unchanged");

        // And it still decrypts back to the original token for the sync services.
        Optional<BrokerCredentialService.DecryptedCredential> dec = service.decryptFor(1L, 100L, Broker.IBKR);
        assertTrue(dec.isPresent());
        assertEquals("original-token", dec.get().secret1());
        assertEquals("queryId-999", dec.get().meta1());
    }

    @Test
    void listStatusesReturnsMaskedViewOnly() {
        service.save(1L, 10L, 100L, Broker.IBKR, "q1", null, "tok1", null);
        service.save(1L, 11L, 101L, Broker.TIGER, "tiger-id", "acct-1", "rsa-key", null);

        List<BrokerCredentialService.CredentialStatus> statuses = service.listStatuses(1L);
        assertEquals(2, statuses.size());
        for (var s : statuses) {
            assertTrue(s.secret1Set(), "each configured credential reports its secret as set");
            assertFalse(s.toString().toLowerCase().contains("tok1"));
            assertFalse(s.toString().toLowerCase().contains("rsa-key"));
        }
    }

    @Test
    void decryptForReturnsPlaintextForSync() {
        service.save(2L, 20L, 200L, Broker.TIGER, "tiger-42", "u-account", "-----BEGIN PRIVATE KEY-----abc", null);
        var dec = service.decryptFor(2L, 200L, Broker.TIGER).orElseThrow();
        assertEquals("tiger-42", dec.meta1());
        assertEquals("u-account", dec.meta2());
        assertEquals("-----BEGIN PRIVATE KEY-----abc", dec.secret1());
        assertNull(dec.secret2());
    }
}
