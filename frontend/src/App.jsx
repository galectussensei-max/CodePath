import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import GuestRoute from './components/GuestRoute.jsx';
import Navbar from './components/Navbar.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import Landing from './pages/Landing.jsx';
import Login from './pages/Auth/Login.jsx';
import Register from './pages/Auth/Register.jsx';
import Onboarding from './pages/Onboarding/Onboarding.jsx';
import Dashboard from './pages/Dashboard/Dashboard.jsx';
import Problems from './pages/Problems/Problems.jsx';
import Workspace from './pages/Workspace/Workspace.jsx';
import Roadmap from './pages/Roadmap/Roadmap.jsx';
import Analytics from './pages/Analytics/Analytics.jsx';

export default function App() {
  // The coding workspace needs the full window width; every other page keeps a centred column.
  const wide = useLocation().pathname.startsWith('/workspace/');
  return (
    <div className="min-h-screen">
      <Navbar />
      <main className={wide ? 'px-4 py-4' : 'mx-auto max-w-6xl px-4 py-8'}>
        <Routes>
          <Route path="/" element={<Landing />} />

          <Route element={<GuestRoute />}>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
          </Route>

          {/* Signed in, diagnostic not required: this is where the diagnostic is taken. */}
          <Route element={<ProtectedRoute requireDiagnostic={false} />}>
            <Route path="/onboarding" element={<Onboarding />} />
          </Route>

          {/* Signed in and diagnostic completed. Phase 7 added /problems and /workspace/:slug; Phase 8 added /roadmap and /analytics. */}
          <Route element={<ProtectedRoute />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/problems" element={<Problems />} />
            <Route path="/workspace/:slug" element={<Workspace />} />
            <Route path="/roadmap" element={<Roadmap />} />
            <Route path="/analytics" element={<Analytics />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}
