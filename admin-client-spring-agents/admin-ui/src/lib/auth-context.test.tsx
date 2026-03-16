import React from 'react';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthProvider, useAuth } from './auth-context';
import { api } from './api';

function Probe() {
  const { isAuthenticated, token, setToken, logout } = useAuth();
  return (
    <div>
      <span data-testid="auth">{String(isAuthenticated)}</span>
      <span data-testid="token">{token ?? ''}</span>
      <button onClick={() => setToken('new-token')}>set</button>
      <button onClick={logout}>logout</button>
    </div>
  );
}

describe('AuthProvider', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('loads token from localStorage on mount', async () => {
    localStorage.setItem('admin_token', 'stored-token');
    const setTokenSpy = vi.spyOn(api, 'setToken');

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('auth')).toHaveTextContent('true');
      expect(screen.getByTestId('token')).toHaveTextContent('stored-token');
    });
    expect(setTokenSpy).toHaveBeenCalledWith('stored-token');
  });

  it('updates and clears token through context actions', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId('auth')).toHaveTextContent('false'));
    await userEvent.click(screen.getByText('set'));
    await waitFor(() => expect(screen.getByTestId('token')).toHaveTextContent('new-token'));
    expect(localStorage.getItem('admin_token')).toBe('new-token');

    await userEvent.click(screen.getByText('logout'));
    await waitFor(() => expect(screen.getByTestId('auth')).toHaveTextContent('false'));
    expect(localStorage.getItem('admin_token')).toBeNull();
  });
});
