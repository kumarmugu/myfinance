import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import ToastContainer from './ToastContainer';
import { ToastProvider, useToast } from '../contexts/ToastContext';
import { act } from '@testing-library/react';

// Helper to render ToastContainer with pre-populated toasts
function TestWrapper() {
  const { showToast } = useToast();
  return (
    <div>
      <button onClick={() => showToast('Error message', 'error')} data-testid="trigger-error">Error</button>
      <button onClick={() => showToast('Success message', 'success')} data-testid="trigger-success">Success</button>
      <button onClick={() => showToast('Info message', 'info')} data-testid="trigger-info">Info</button>
      <ToastContainer />
    </div>
  );
}

function renderWithProvider() {
  return render(
    <ToastProvider>
      <TestWrapper />
    </ToastProvider>
  );
}

describe('ToastContainer', () => {
  it('renders nothing when no toasts', () => {
    render(<ToastProvider><ToastContainer /></ToastProvider>);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('renders error toast with correct styling', () => {
    renderWithProvider();
    act(() => { screen.getByTestId('trigger-error').click(); });
    const alert = screen.getByRole('alert');
    expect(alert).toBeInTheDocument();
    expect(alert).toHaveTextContent('Error message');
    expect(alert.className).toContain('bg-red-50');
  });

  it('renders success toast with correct styling', () => {
    renderWithProvider();
    act(() => { screen.getByTestId('trigger-success').click(); });
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Success message');
    expect(alert.className).toContain('bg-green-50');
  });

  it('renders info toast with correct styling', () => {
    renderWithProvider();
    act(() => { screen.getByTestId('trigger-info').click(); });
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Info message');
    expect(alert.className).toContain('bg-blue-50');
  });

  it('dismisses toast on X button click', () => {
    renderWithProvider();
    act(() => { screen.getByTestId('trigger-error').click(); });
    expect(screen.getByRole('alert')).toBeInTheDocument();

    const dismissBtn = screen.getByLabelText('Dismiss');
    fireEvent.click(dismissBtn);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('renders multiple toasts stacked', () => {
    renderWithProvider();
    act(() => {
      screen.getByTestId('trigger-error').click();
      screen.getByTestId('trigger-success').click();
    });
    const alerts = screen.getAllByRole('alert');
    expect(alerts).toHaveLength(2);
  });

  it('preserves newlines in messages', () => {
    function MultilineWrapper() {
      const { showToast } = useToast();
      return (
        <div>
          <button onClick={() => showToast('Line 1\nLine 2', 'error')} data-testid="trigger">Go</button>
          <ToastContainer />
        </div>
      );
    }
    render(<ToastProvider><MultilineWrapper /></ToastProvider>);
    act(() => { screen.getByTestId('trigger').click(); });
    expect(screen.getByRole('alert')).toHaveTextContent('Line 1');
    expect(screen.getByRole('alert')).toHaveTextContent('Line 2');
  });
});
