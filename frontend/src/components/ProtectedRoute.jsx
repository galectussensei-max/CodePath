import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/useAuth.js';

/**
 * Guards signed-in pages. Use it as a layout route (<Route element={<ProtectedRoute />}>) or wrap
 * children directly.
 *  - Not signed in: redirect to /login, remembering where the user was heading.
 *  - Signed in but diagnostic not done: redirect to /onboarding (unless requireDiagnostic is false,
 *    which is how the onboarding page itself is mounted).
 */
export default function ProtectedRoute({ requireDiagnostic = true, children }) {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  if (requireDiagnostic && !user.diagnosticCompleted) {
    return <Navigate to="/onboarding" replace />;
  }
  return children ?? <Outlet />;
}
