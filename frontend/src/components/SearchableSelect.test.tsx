import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import SearchableSelect from './SearchableSelect';

const options = [
  { value: 1, label: 'Apple' },
  { value: 2, label: 'Banana' },
  { value: 3, label: 'Cherry' },
];

describe('SearchableSelect', () => {
  it('renders with placeholder when no value selected', () => {
    render(<SearchableSelect options={options} value={0} onChange={() => {}} placeholder="Pick a fruit..." />);
    expect(screen.getByText('Pick a fruit...')).toBeInTheDocument();
  });

  it('shows selected value label', () => {
    render(<SearchableSelect options={options} value={2} onChange={() => {}} />);
    expect(screen.getByText('Banana')).toBeInTheDocument();
  });

  it('opens dropdown on click', () => {
    render(<SearchableSelect options={options} value={0} onChange={() => {}} />);
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByPlaceholderText('Search...')).toBeInTheDocument();
  });

  it('filters options by search text', () => {
    render(<SearchableSelect options={options} value={0} onChange={() => {}} />);
    fireEvent.click(screen.getByRole('button'));
    fireEvent.change(screen.getByPlaceholderText('Search...'), { target: { value: 'ban' } });
    expect(screen.getByText('Banana')).toBeInTheDocument();
    expect(screen.queryByText('Apple')).not.toBeInTheDocument();
    expect(screen.queryByText('Cherry')).not.toBeInTheDocument();
  });

  it('calls onChange when option is selected', () => {
    const onChange = vi.fn();
    render(<SearchableSelect options={options} value={0} onChange={onChange} />);
    fireEvent.click(screen.getByRole('button'));
    fireEvent.click(screen.getByText('Cherry'));
    expect(onChange).toHaveBeenCalledWith(3);
  });

  it('shows "No results" when search matches nothing', () => {
    render(<SearchableSelect options={options} value={0} onChange={() => {}} />);
    fireEvent.click(screen.getByRole('button'));
    fireEvent.change(screen.getByPlaceholderText('Search...'), { target: { value: 'xyz' } });
    expect(screen.getByText('No results')).toBeInTheDocument();
  });

  it('respects disabled prop', () => {
    render(<SearchableSelect options={options} value={1} onChange={() => {}} disabled />);
    const btn = screen.getByRole('button');
    expect(btn).toBeDisabled();
  });
});
