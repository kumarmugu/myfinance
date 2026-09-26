import { describe, it, expect } from 'vitest';
import { parseSplitRatio } from './stockSplit';

describe('parseSplitRatio', () => {
  it('parses a forward split "3:1"', () => {
    expect(parseSplitRatio('3:1')).toEqual({ numerator: 3, denominator: 1 });
  });

  it('parses a reverse split "1:8"', () => {
    expect(parseSplitRatio('1:8')).toEqual({ numerator: 1, denominator: 8 });
  });

  it('treats a plain number as N:1', () => {
    expect(parseSplitRatio('3')).toEqual({ numerator: 3, denominator: 1 });
  });

  it('tolerates surrounding whitespace and spaces around the colon', () => {
    expect(parseSplitRatio('  20 : 1 ')).toEqual({ numerator: 20, denominator: 1 });
  });

  it('accepts decimal ratios', () => {
    expect(parseSplitRatio('1.5:1')).toEqual({ numerator: 1.5, denominator: 1 });
  });

  it('rejects zero or negative values', () => {
    expect(parseSplitRatio('0:1')).toBeNull();
    expect(parseSplitRatio('3:0')).toBeNull();
    expect(parseSplitRatio('-3:1')).toBeNull();
  });

  it('rejects non-numeric / malformed input', () => {
    expect(parseSplitRatio('')).toBeNull();
    expect(parseSplitRatio('abc')).toBeNull();
    expect(parseSplitRatio('3:1:2')).toBeNull();
    expect(parseSplitRatio('3/1')).toBeNull();
  });
});
