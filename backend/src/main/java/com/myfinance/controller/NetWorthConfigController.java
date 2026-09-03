package com.myfinance.controller;

import com.myfinance.model.NetWorthConfig;
import com.myfinance.model.enums.AssetType;
import com.myfinance.repository.NetWorthConfigRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/net-worth-config")
@RequiredArgsConstructor
@Slf4j
public class NetWorthConfigController {
    private final NetWorthConfigRepository repository;
    private final TenantContext tenantContext;

    /**
     * Config keys for the standalone asset modules that live in their own tables
     * (not brokerage Holdings). These are toggled in the same Net Worth Config
     * screen alongside the {@link AssetType} keys. Kept in sync with
     * {@code NetWorthService} which reads these to decide module inclusion.
     */
    public static final Map<String, String> MODULE_KEYS = new LinkedHashMap<>() {{
        put("BANK_SAVINGS", "Bank Savings");
        put("PROPERTY", "Real Estate");
        put("PRECIOUS_METAL", "Precious Metals");
        put("GENERIC_FD", "Fixed Deposits");
        put("CASH", "Broker Cash");
    }};

    @GetMapping
    public List<NetWorthConfig> getAll() {
        Long uid = tenantContext.getCurrentUserId();
        List<NetWorthConfig> existing = repository.findByUserIdOrderByAssetTypeAsc(uid);

        // Ensure all asset types AND standalone modules have a config entry (auto-create missing ones)
        Set<String> existingTypes = existing.stream().map(NetWorthConfig::getAssetType).collect(Collectors.toSet());
        List<NetWorthConfig> toCreate = new ArrayList<>();

        for (AssetType type : AssetType.values()) {
            if (!existingTypes.contains(type.name())) {
                toCreate.add(NetWorthConfig.builder()
                        .assetType(type.name())
                        .includeInNetWorth(true)
                        .label(formatLabel(type.name())).userId(uid)
                        .build());
            }
        }

        MODULE_KEYS.forEach((key, label) -> {
            if (!existingTypes.contains(key)) {
                toCreate.add(NetWorthConfig.builder()
                        .assetType(key)
                        .includeInNetWorth(true)
                        .label(label).userId(uid)
                        .build());
            }
        });

        if (!toCreate.isEmpty()) {
            repository.saveAll(toCreate);
            existing = repository.findByUserIdOrderByAssetTypeAsc(uid);
        }

        // Refresh the stale legacy "Savings" label for existing config rows so it no longer
        // reads the same as the Bank Savings module. Idempotent; only writes when needed.
        existing.stream()
                .filter(c -> "SAVINGS".equals(c.getAssetType()) && !"Cash / Savings (holding)".equals(c.getLabel()))
                .findFirst()
                .ifPresent(c -> { c.setLabel("Cash / Savings (holding)"); repository.save(c); });

        return existing;
    }

    @PutMapping("/{id}")
    public NetWorthConfig update(@PathVariable Long id, @RequestBody NetWorthConfig updated) {
        log.info("Updating net-worth config id={}, includeInNetWorth={}", id, updated.getIncludeInNetWorth());
        NetWorthConfig existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        existing.setIncludeInNetWorth(updated.getIncludeInNetWorth());
        if (updated.getLabel() != null) existing.setLabel(updated.getLabel());
        return repository.save(existing);
    }

    @PutMapping("/batch")
    public List<NetWorthConfig> batchUpdate(@RequestBody List<NetWorthConfig> configs) {
        log.info("Batch updating {} net-worth configs", configs.size());
        return repository.saveAll(configs);
    }

    @GetMapping("/included-types")
    public List<String> getIncludedTypes() {
        return repository.findByUserIdAndIncludeInNetWorthTrue(tenantContext.getCurrentUserId()).stream()
                .map(NetWorthConfig::getAssetType)
                .collect(Collectors.toList());
    }

    private String formatLabel(String enumName) {
        // SAVINGS (an investment holding type) is easily confused with the standalone Bank Savings
        // module; disambiguate its label. Bank Savings uses its own BANK_SAVINGS config key.
        if ("SAVINGS".equals(enumName)) return "Cash / Savings (holding)";
        return Arrays.stream(enumName.split("_"))
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
