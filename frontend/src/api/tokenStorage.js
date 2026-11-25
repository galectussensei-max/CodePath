// Single place that touches localStorage for the session. The JWT is the
// backend's `accessToken`; `expiresAt` is derived from `expiresInMs`.
const KEY = 'codepath.session';

export function saveSession({ accessToken, expiresInMs, user }) {
  const session = {
    accessToken,
    user,
    expiresAt: Date.now() + (Number(expiresInMs) || 0),
  };
  try {
    localStorage.setItem(KEY, JSON.stringify(session));
  } catch {
    // Storage unavailable (private mode / quota): the session lives in memory only.
  }
  return session;
}

export function loadSession() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const session = JSON.parse(raw);
    if (!session || !session.accessToken || !session.user) return null;
    if (session.expiresAt && Date.now() >= session.expiresAt) {
      localStorage.removeItem(KEY);
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function clearSession() {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // ignore
  }
}

export function getToken() {
  const session = loadSession();
  return session ? session.accessToken : null;
}
