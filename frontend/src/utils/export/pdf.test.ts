import { describe, it, expect } from 'vitest';
import { buildPdfBlob } from './pdf';
import type { ExportConfig } from './types';

interface Row { id: number; a: string; b: string; c: string; d: string; e: string; f: string; g: string; }

const wideConfig: ExportConfig<Row> = {
  entity: 'wide',
  title: 'Wide Report',
  columns: [
    { key: 'id', header: 'ID', accessor: r => r.id, type: 'number' },
    { key: 'a', header: 'A', accessor: r => r.a },
    { key: 'b', header: 'B', accessor: r => r.b },
    { key: 'c', header: 'C', accessor: r => r.c },
    { key: 'd', header: 'D', accessor: r => r.d },
    { key: 'e', header: 'E', accessor: r => r.e },
    { key: 'f', header: 'F', accessor: r => r.f },
    { key: 'g', header: 'G', accessor: r => r.g },
  ],
};

const narrowConfig: ExportConfig<{ id: number; name: string }> = {
  entity: 'narrow',
  title: 'Narrow Report',
  columns: [
    { key: 'id', header: 'ID', accessor: r => r.id, type: 'number' },
    { key: 'name', header: 'Name', accessor: r => r.name },
  ],
};

function makeWideRows(n: number): Row[] {
  return Array.from({ length: n }, (_, i) => ({
    id: i, a: `a${i}`, b: `b${i}`, c: `c${i}`, d: `d${i}`,
    e: 'long text '.repeat(20), f: `f${i}`, g: `g${i}`,
  }));
}

describe('export/pdf', () => {
  it('produces a PDF blob', () => {
    const blob = buildPdfBlob(makeWideRows(3), wideConfig);
    expect(blob).toBeInstanceOf(Blob);
    expect(blob.type).toContain('pdf');
    expect(blob.size).toBeGreaterThan(0);
  });

  it('generates a non-trivial document for wide tables with many columns', () => {
    // > 6 columns triggers landscape internally; just assert it renders without throwing.
    const blob = buildPdfBlob(makeWideRows(1), wideConfig);
    expect(blob.size).toBeGreaterThan(500);
  });

  it('handles multi-page (large) datasets and long text', () => {
    const blob = buildPdfBlob(makeWideRows(500), wideConfig);
    expect(blob.size).toBeGreaterThan(0);
  });

  it('handles empty datasets (header only)', () => {
    const blob = buildPdfBlob([], narrowConfig);
    expect(blob).toBeInstanceOf(Blob);
    expect(blob.size).toBeGreaterThan(0);
  });

  it('accepts a subtitle without throwing', () => {
    const blob = buildPdfBlob([{ id: 1, name: 'x' }], narrowConfig, { subtitle: 'Filtered: USD, 2026' });
    expect(blob.size).toBeGreaterThan(0);
  });
});
