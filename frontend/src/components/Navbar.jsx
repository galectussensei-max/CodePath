import { Link, useNavigate } from 'react-router-dom';
import { Code2, LogOut } from 'lucide-react';
import { useAuth } from '../context/useAuth.js';
import { homePathFor } from '../context/authRouting.js';

export default function Navbar() {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
        <Link
          to={isAuthenticated ? homePathFor(user) : '/'}
          className="flex items-center gap-2 text-lg font-semibold text-brand-700"
        >
          <Code2 className="h-5 w-5" aria-hidden="true" />
          CodePath
        </Link>

        {isAuthenticated ? (
          <div className="flex items-center gap-4 text-sm">
            {user.diagnosticCompleted && (
              <>
                <Link to="/dashboard" className="font-medium text-slate-700 hover:text-brand-700">
                  Dashboard
                </Link>
                <Link to="/problems" className="font-medium text-slate-700 hover:text-brand-700">
                  Problems
                </Link>
                <Link to="/roadmap" className="font-medium text-slate-700 hover:text-brand-700">
                  Roadmap
                </Link>
                <Link to="/analytics" className="font-medium text-slate-700 hover:text-brand-700">
                  Analytics
                </Link>
              </>
            )}
            <span className="text-slate-600">{user.name}</span>
            <button
              type="button"
              onClick={handleLogout}
              className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-3 py-1.5 text-slate-700 hover:bg-slate-100"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
              Log out
            </button>
          </div>
        ) : (
          <div className="flex items-center gap-3 text-sm">
            <Link to="/login" className="font-medium text-slate-700 hover:text-brand-700">
              Log in
            </Link>
            <Link
              to="/register"
              className="rounded-md bg-brand-600 px-3 py-1.5 font-semibold text-white hover:bg-brand-700"
            >
              Sign up
            </Link>
          </div>
        )}
      </div>
    </header>
  );
}
