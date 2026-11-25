import { createContext, useCallback, useEffect, useMemo, useState } from 'react';
import { UNAUTHORIZED_EVENT } from '../api/axiosInstance.js';
import * as authService from '../api/authService.js';
import { clearSession, loadSession, saveSession } from '../api/tokenStorage.js';

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // Lazy initialiser: read the stored session once, synchronously, so protected
  // routes never flash a logged-out state on refresh.
  const [session, setSession] = useState(() => loadSession());

  const applyAuthResponse = useCallback((authResponse) => {
    const saved = saveSession(authResponse);
    setSession(saved);
    return saved.user;
  }, []);

  const login = useCallback(
    async (email, password) => applyAuthResponse(await authService.login(email, password)),
    [applyAuthResponse]
  );

  const register = useCallback(
    async (name, email, password) =>
      applyAuthResponse(await authService.register(name, email, password)),
    [applyAuthResponse]
  );

  const logout = useCallback(() => {
    clearSession();
    setSession(null);
  }, []);

  // Merge changes into the stored user, e.g. { diagnosticCompleted: true } after the diagnostic.
  const updateUser = useCallback((patch) => {
    setSession((current) => {
      if (!current) return current;
      const next = { ...current, user: { ...current.user, ...patch } };
      saveSession({
        accessToken: next.accessToken,
        user: next.user,
        expiresInMs: Math.max(0, next.expiresAt - Date.now()),
      });
      return next;
    });
  }, []);

  // The axios interceptor fires this when a protected call returns 401.
  useEffect(() => {
    const onUnauthorized = () => setSession(null);
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, []);

  const value = useMemo(
    () => ({
      user: session ? session.user : null,
      token: session ? session.accessToken : null,
      isAuthenticated: Boolean(session),
      login,
      register,
      logout,
      updateUser,
    }),
    [session, login, register, logout, updateUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
