package com.myfinance.security;

import com.myfinance.model.AppUser;

/**
 * Per-user feature-flag checks against {@code AppUser.enabledFeatures} (a CSV). An empty/blank CSV
 * means all features are enabled (project convention for backward compatibility).
 */
public final class FeatureFlags {

    /** Live broker sync (IBKR/Tiger/Saxo). Renamed from IBKR_SYNC; the old key still works. */
    public static final String BROKER_SYNC = "BROKER_SYNC";
    public static final String LEGACY_IBKR_SYNC = "IBKR_SYNC";

    private FeatureFlags() {}

    public static boolean has(AppUser user, String key) {
        if (user == null) return false;
        String csv = user.getEnabledFeatures();
        if (csv == null || csv.isBlank()) return true; // empty = all enabled
        for (String f : csv.split(",")) if (key.equals(f.trim())) return true;
        return false;
    }

    /** Broker sync is granted by BROKER_SYNC or (for back-compat) the old IBKR_SYNC key. */
    public static boolean hasBrokerSync(AppUser user) {
        if (user == null) return false;
        String csv = user.getEnabledFeatures();
        if (csv == null || csv.isBlank()) return true;
        for (String f : csv.split(",")) {
            String t = f.trim();
            if (BROKER_SYNC.equals(t) || LEGACY_IBKR_SYNC.equals(t)) return true;
        }
        return false;
    }
}
