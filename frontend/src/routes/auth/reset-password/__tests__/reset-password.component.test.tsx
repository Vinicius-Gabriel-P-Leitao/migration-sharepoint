import { render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { describe, it, expect, vi, beforeEach, type Mock } from "vitest";
import { ResetPasswordPage } from '../reset-password.component';
import { useNavigate } from "@tanstack/react-router";
import { useMutation } from "@tanstack/react-query";
import { useAuthStore } from "@lib/store/auth.store";
import type * as Router from "@tanstack/react-router";

// Mocks
vi.mock("@tanstack/react-router", async (importOriginal: () => Promise<typeof Router>) => {
  const actual = await importOriginal();
  return {
    ...actual,
    useNavigate: vi.fn(),
    Navigate: vi.fn(({ to }: { to: string }) => <div data-testid="navigate" data-to={to} />),
  };
});

vi.mock("@tanstack/react-query", () => ({
  useMutation: vi.fn(),
}));

vi.mock("@lib/store/auth.store", () => ({
  useAuthStore: vi.fn(),
}));

vi.mock("../services/auth.service", () => ({
  firstChangePasswordAttempt: vi.fn(),
}));

describe("ResetPasswordPage", () => {
  const navigate = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    (useNavigate as Mock).mockReturnValue(navigate);
    (useMutation as Mock).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    (useAuthStore as unknown as Mock).mockReturnValue({
      isAuthenticated: true,
      passwordResetRequired: true,
      user: { profile: { username: "testuser" } },
    });
  });

  it("renders reset password form elements", () => {
    render(<ResetPasswordPage />);
    expect(screen.getByText(/definir nova senha/i)).toBeInTheDocument();
    expect(screen.getByText(/testuser/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /atualizar e sair/i })).toBeInTheDocument();
  });
});
