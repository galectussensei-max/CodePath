import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/useAuth.js';
import { homePathFor, returnPathFrom } from '../context/authRouting.js';

/**
 * Wraps /login and /register. A signed-in user is sent onward: back to the page they were trying to
 * reach (only if their diagnostic is done), otherwise to their home page. Because the redirect lives
 * here, Login and Register only have to call the auth context; nothing else navigates after sign-in.
 */
export default function GuestRoute({ children }) {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();

  if (isAuthenticated) {
    const returnPath = user.diagnosticCompleted ? returnPathFrom(location) : null;
    return <Navigate to={returnPath || homePathFor(user)} replace />;
  }
  return children ?? <Outlet />;
}
