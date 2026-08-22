package com.myfinance.controller;

import com.myfinance.model.NetWorthConfig;
import com.myfinance.model.enums.AssetType;
import com.myfinance.repository.NetWorthConfigRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/net-worth-config")
@RequiredArgsConstructor
public class NetWorthConfigController {
    private final NetWorthConfigRepository repository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<NetWorthConfig> getAll() {
        Long uid = tenantContext.getCurrentUserId();
        List<NetWorthConfig> existing = repository.findByUserIdOrderByAssetTypeAsc(uid);

        // Ensure all asset types have a config entry (auto-create missing ones)
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

        if (!toCreate.isEmpty()) {
            repository.saveAll(toCreate);
            existing = repository.findByUserIdOrderByAssetTypeAsc(uid);
        }

        return existing;
    }

    @PutMapping("/{id}")
    public NetWorthConfig update(@PathVariable Long id, @RequestBody NetWorthConfig updated) {
        NetWorthConfig existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        existing.setIncludeInNetWorth(updated.getIncludeInNetWorth());
        if (updated.getLabel() != null) existing.setLabel(updated.getLabel());
        return repository.save(existing);
    }

    @PutMapping("/batch")
    public List<NetWorthConfig> batchUpdate(@RequestBody List<NetWorthConfig> configs) {
        return repository.saveAll(configs);
    }

    @GetMapping("/included-types")
    public List<String> getIncludedTypes() {
        return repository.findByUserIdAndIncludeInNetWorthTrue(tenantContext.getCurrentUserId()).stream()
                .map(NetWorthConfig::getAssetType)
                .collect(Collectors.toList());
    }

    private String formatLabel(String enumName) {
        return Arrays.stream(enumName.split("_"))
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
