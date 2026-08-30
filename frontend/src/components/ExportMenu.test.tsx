import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock the export orchestration so tests don't generate real files.
const exportTable = vi.fn();
vi.mock('../utils/export', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../utils/export')>();
  return { ...actual, exportTable: (...args: any[]) => exportTable(...args) };
});

// Capture toast calls.
const showToast = vi.fn();
vi.mock('../contexts/ToastContext', () => ({
  useToast: () => ({ showToast }),
}));

import ExportMenu from './ExportMenu';
import type { ExportConfig } from '../utils/export';

interface Row { id: number; name: string; }
const config: ExportConfig<Row> = {
  entity: 'widgets',
  title: 'Widgets',
  columns: [
    { key: 'id', header: 'ID', accessor: r => r.id, type: 'number' },
    { key: 'name', header: 'Name', accessor: r => r.name },
  ],
};
const rows: Row[] = [{ id: 1, name: 'a' }, { id: 2, name: 'b' }];

describe('ExportMenu', () => {
  beforeEach(() => {
    exportTable.mockReset();
    exportTable.mockResolvedValue(undefined);
    showToast.mockReset();
  });

  it('renders an Export button', () => {
    render(<ExportMenu rows={rows} config={config} />);
    expect(screen.getByRole('button', { name: /export table/i })).toBeInTheDocument();
  });

  it('opens the menu and shows CSV, Excel and PDF options', () => {
    render(<ExportMenu rows={rows} config={config} />);
    fireEvent.click(screen.getByRole('button', { name: /export table/i }));
    expect(screen.getByRole('menu')).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'CSV' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Excel' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'PDF' })).toBeInTheDocument();
  });

  it('triggers CSV export with rows and config', async () => {
    render(<ExportMenu rows={rows} config={config} />);
    fireEvent.click(screen.getByRole('button', { name: /export table/i }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'CSV' }));
    await waitFor(() => expect(exportTable).toHaveBeenCalledTimes(1));
    expect(exportTable).toHaveBeenCalledWith(rows, config, 'csv', {});
  });

  it('triggers Excel export', async () => {
    render(<ExportMenu rows={rows} config={config} />);
    fireEvent.click(screen.getByRole('button', { name: /export table/i }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Excel' }));
    await waitFor(() => expect(exportTable).toHaveBeenCalledWith(rows, config, 'excel', {}));
  });

  it('triggers PDF export and passes subtitle when provided', async () => {
    render(<ExportMenu rows={rows} config={config} subtitle="Filtered: USD" />);
    fireEvent.click(screen.getByRole('button', { name: /export table/i }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'PDF' }));
    await waitFor(() => expect(exportTable).toHaveBeenCalledWith(rows, config, 'pdf', { subtitle: 'Filtered: USD' }));
  });

  it('shows a success toast on successful export', async () => {
    render(<ExportMenu rows={rows} config={config} />);
    fireEvent.click(screen.getByRole('button', { name: /export table/i }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'CSV' }));
    await waitFor(() => expect(showToast).toHaveBeenCalledWith('Your export is ready.', 'success'));
  });

  it('shows an error toast (no stack trace) on failure', async () => {
    exportTable.mockRejectedValueOnce(new Error('boom internal detail'));
    render(<ExportMenu rows={rows} config={config} />);
    fireEvent.click(screen.getByRole('button', { name: /export table/i }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'CSV' }));
    await waitFor(() =>
      expect(showToast).toHaveBeenCalledWith('Unable to export the data. Please try again.', 'error'),
    );
    // Never leaks internal error text to the user.
    expect(showToast).not.toHaveBeenCalledWith(expect.stringContaining('boom'), expect.anything());
  });

  it('disables the control and shows guidance when the dataset is empty', () => {
    render(<ExportMenu rows={[]} config={config} />);
    const btn = screen.getByRole('button', { name: /no data available/i });
    expect(btn).toBeDisabled();
  });

  it('does not run a second export while one is in flight (duplicate-click guard)', async () => {
    let resolve!: () => void;
    exportTable.mockImplementation(() => new Promise<void>(r => { resolve = () => r(); }));
    render(<ExportMenu rows={rows} config={config} />);

    fireEvent.click(screen.getByRole('button', { name: /export table/i }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'CSV' }));

    // While exporting the button shows the loading state and is disabled.
    await waitFor(() => expect(screen.getByText('Exporting...')).toBeInTheDocument());
    expect(screen.getByRole('button')).toBeDisabled();

    resolve();
    await waitFor(() => expect(showToast).toHaveBeenCalledWith('Your export is ready.', 'success'));
    expect(exportTable).toHaveBeenCalledTimes(1);
  });

  it('supports keyboard navigation to open and move within the menu', () => {
    render(<ExportMenu rows={rows} config={config} />);
    const btn = screen.getByRole('button', { name: /export table/i });
    btn.focus();
    fireEvent.keyDown(btn, { key: 'ArrowDown' });
    expect(screen.getByRole('menu')).toBeInTheDocument();
    // First item focused by default.
    expect(screen.getByRole('menuitem', { name: 'CSV' })).toHaveFocus();
    fireEvent.keyDown(screen.getByRole('menu'), { key: 'ArrowDown' });
    expect(screen.getByRole('menuitem', { name: 'Excel' })).toHaveFocus();
  });

  it('closes the menu on Escape', () => {
    render(<ExportMenu rows={rows} config={config} />);
    fireEvent.click(screen.getByRole('button', { name: /export table/i }));
    fireEvent.keyDown(screen.getByRole('menu'), { key: 'Escape' });
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });
});
