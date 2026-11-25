/** Where a signed-in user belongs: the dashboard once the diagnostic is done, onboarding before. */
export function homePathFor(user) {
  return user && user.diagnosticCompleted ? '/dashboard' : '/onboarding';
}

const AUTH_PATHS = ['/login', '/register', '/onboarding'];

/** A "come back to" path left by ProtectedRoute, or null when there is none worth returning to. */
export function returnPathFrom(location) {
  const from = location?.state?.from;
  if (!from || typeof from.pathname !== 'string') return null;
  if (AUTH_PATHS.some((path) => from.pathname.startsWith(path))) return null;
  return `${from.pathname}${from.search || ''}${from.hash || ''}`;
}
