import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach, type Mock } from 'vitest';
import { LoginPage } from '../login.component';
import { useNavigate } from '@tanstack/react-router';
import { useMutation } from '@tanstack/react-query';

// Mocks
vi.mock('@tanstack/react-router', () => ({
  useNavigate: vi.fn(),
}));

vi.mock('@tanstack/react-query', () => ({
  useMutation: vi.fn(),
}));

vi.mock('../services/auth.service', () => ({
  loginAttempt: vi.fn(),
}));

describe('LoginPage', () => {
  const navigate = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    (useNavigate as Mock).mockReturnValue(navigate);
    (useMutation as Mock).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
  });

  it('renders login form elements', () => {
    render(<LoginPage />);
    expect(screen.getByText(/Migração/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/admin@exemplo.com/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /entrar/i })).toBeInTheDocument();
  });
});
