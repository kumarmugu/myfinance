package com.myfinance.security;

import com.myfinance.model.AppUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link FeatureFlags#hasBrokerSync}: the new BROKER_SYNC key and the legacy IBKR_SYNC key both
 * grant broker sync; an empty CSV means all features enabled (project convention); an unrelated CSV
 * does not grant it; and a null user is denied.
 */
class FeatureFlagsTest {

    private AppUser userWith(String features) {
        AppUser u = new AppUser();
        u.setEnabledFeatures(features);
        return u;
    }

    @Test
    void brokerSyncKeyGrantsBrokerSync() {
        assertTrue(FeatureFlags.hasBrokerSync(userWith("BROKER_SYNC")));
        assertTrue(FeatureFlags.hasBrokerSync(userWith("EXPENSES,BROKER_SYNC,TAX")));
    }

    @Test
    void legacyIbkrSyncKeyStillGrantsBrokerSync() {
        assertTrue(FeatureFlags.hasBrokerSync(userWith("IBKR_SYNC")),
                "the old IBKR_SYNC key must keep working for back-compat");
    }

    @Test
    void emptyCsvMeansAllEnabled() {
        assertTrue(FeatureFlags.hasBrokerSync(userWith("")));
        assertTrue(FeatureFlags.hasBrokerSync(userWith(null)));
    }

    @Test
    void unrelatedFeaturesDoNotGrantBrokerSync() {
        assertFalse(FeatureFlags.hasBrokerSync(userWith("EXPENSES,TAX")));
    }

    @Test
    void nullUserIsDenied() {
        assertFalse(FeatureFlags.hasBrokerSync(null));
    }
}
