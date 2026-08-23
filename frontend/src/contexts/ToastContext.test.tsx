import { render, screen, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ToastProvider, useToast } from './ToastContext';

// Test component that uses the toast context
function TestConsumer() {
  const { toasts, showToast, removeToast } = useToast();
  return (
    <div>
      <button onClick={() => showToast('Error occurred', 'error')} data-testid="show-error">Show Error</button>
      <button onClick={() => showToast('Success!', 'success')} data-testid="show-success">Show Success</button>
      <button onClick={() => showToast('Info message', 'info')} data-testid="show-info">Show Info</button>
      <div data-testid="toast-count">{toasts.length}</div>
      {toasts.map(t => (
        <div key={t.id} data-testid={`toast-${t.type}`}>
          <span>{t.message}</span>
          <button onClick={() => removeToast(t.id)} data-testid={`remove-${t.id}`}>X</button>
        </div>
      ))}
    </div>
  );
}

describe('ToastContext', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it('provides empty toasts initially', () => {
    render(<ToastProvider><TestConsumer /></ToastProvider>);
    expect(screen.getByTestId('toast-count').textContent).toBe('0');
  });

  it('shows error toast', () => {
    render(<ToastProvider><TestConsumer /></ToastProvider>);
    act(() => { screen.getByTestId('show-error').click(); });
    expect(screen.getByTestId('toast-count').textContent).toBe('1');
    expect(screen.getByTestId('toast-error')).toBeInTheDocument();
    expect(screen.getByText('Error occurred')).toBeInTheDocument();
  });

  it('shows success toast', () => {
    render(<ToastProvider><TestConsumer /></ToastProvider>);
    act(() => { screen.getByTestId('show-success').click(); });
    expect(screen.getByTestId('toast-success')).toBeInTheDocument();
    expect(screen.getByText('Success!')).toBeInTheDocument();
  });

  it('shows info toast', () => {
    render(<ToastProvider><TestConsumer /></ToastProvider>);
    act(() => { screen.getByTestId('show-info').click(); });
    expect(screen.getByTestId('toast-info')).toBeInTheDocument();
    expect(screen.getByText('Info message')).toBeInTheDocument();
  });

  it('supports multiple simultaneous toasts', () => {
    render(<ToastProvider><TestConsumer /></ToastProvider>);
    act(() => {
      screen.getByTestId('show-error').click();
      screen.getByTestId('show-success').click();
    });
    expect(screen.getByTestId('toast-count').textContent).toBe('2');
  });

  it('removes toast manually', () => {
    render(<ToastProvider><TestConsumer /></ToastProvider>);
    act(() => { screen.getByTestId('show-error').click(); });
    expect(screen.getByTestId('toast-count').textContent).toBe('1');

    const removeBtn = screen.getByTestId('toast-error').querySelector('[data-testid^="remove-"]') as HTMLElement;
    act(() => { removeBtn.click(); });
    expect(screen.getByTestId('toast-count').textContent).toBe('0');
  });

  it('auto-dismisses after 4 seconds', () => {
    render(<ToastProvider><TestConsumer /></ToastProvider>);
    act(() => { screen.getByTestId('show-error').click(); });
    expect(screen.getByTestId('toast-count').textContent).toBe('1');

    act(() => { vi.advanceTimersByTime(4000); });
    expect(screen.getByTestId('toast-count').textContent).toBe('0');
  });

  it('defaults to error type when no type specified', () => {
    function DefaultTypeConsumer() {
      const { toasts, showToast } = useToast();
      return (
        <div>
          <button onClick={() => showToast('default type')} data-testid="show-default">Show</button>
          {toasts.map(t => <span key={t.id} data-testid={`type-${t.type}`}>{t.message}</span>)}
        </div>
      );
    }
    render(<ToastProvider><DefaultTypeConsumer /></ToastProvider>);
    act(() => { screen.getByTestId('show-default').click(); });
    expect(screen.getByTestId('type-error')).toBeInTheDocument();
  });

  it('throws error when used outside provider', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<TestConsumer />)).toThrow('useToast must be used within ToastProvider');
    consoleError.mockRestore();
  });
});
