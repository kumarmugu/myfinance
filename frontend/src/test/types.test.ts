import { describe, it, expect } from 'vitest';
import { ASSET_TYPE_LABELS, ASSET_TYPE_COLORS } from '../types';

describe('Type definitions', () => {
  it('ASSET_TYPE_LABELS has all expected types', () => {
    expect(ASSET_TYPE_LABELS.INDEX_FUND).toBe('Index Fund');
    expect(ASSET_TYPE_LABELS.GOLD).toBe('Gold');
    expect(ASSET_TYPE_LABELS.REIT).toBe('REIT');
    expect(ASSET_TYPE_LABELS.BOND).toBe('Bond');
    expect(ASSET_TYPE_LABELS.CRYPTO).toBe('Crypto');
    expect(ASSET_TYPE_LABELS.OTHER).toBe('Other');
  });

  it('ASSET_TYPE_COLORS has colors for all types in labels', () => {
    const labelKeys = Object.keys(ASSET_TYPE_LABELS);
    const colorKeys = Object.keys(ASSET_TYPE_COLORS);
    labelKeys.forEach(key => {
      expect(colorKeys).toContain(key);
      expect(ASSET_TYPE_COLORS[key]).toMatch(/^#[0-9a-f]{6}$/i);
    });
  });

  it('has 16 asset types', () => {
    expect(Object.keys(ASSET_TYPE_LABELS).length).toBe(16);
  });
});
