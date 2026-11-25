import { Link, Navigate } from 'react-router-dom';
import { BrainCircuit, ClipboardCheck, ShieldCheck, Sparkles } from 'lucide-react';
import { useAuth } from '../context/useAuth.js';
import { homePathFor } from '../context/authRouting.js';

const FEATURES = [
  {
    icon: ClipboardCheck,
    title: 'Diagnostic first',
    text: 'A short assessment sets your starting point instead of guessing what to study.',
  },
  {
    icon: BrainCircuit,
    title: 'Transparent mastery',
    text: 'Topic mastery comes from clear rules you can read, and every recommendation says why.',
  },
  {
    icon: ShieldCheck,
    title: 'Verified verdicts',
    text: 'Java solutions are judged in an isolated sandbox, never by an AI opinion.',
  },
  {
    icon: Sparkles,
    title: 'Staged AI hints',
    text: 'Four levels of guidance that never hand you the full solution, always labelled unverified.',
  },
];

export default function Landing() {
  const { isAuthenticated, user } = useAuth();

  if (isAuthenticated) {
    return <Navigate to={homePathFor(user)} replace />;
  }

  return (
    <div className="space-y-8">
      <section className="rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-3xl font-bold text-slate-900">CodePath</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          An AI-powered adaptive DSA learning and placement preparation platform. Know what to study next, and why.
        </p>
        <div className="mt-6 flex gap-3">
          <Link
            to="/register"
            className="rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700"
          >
            Get started
          </Link>
          <Link
            to="/login"
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
          >
            Log in
          </Link>
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-2">
        {FEATURES.map(({ icon: Icon, title, text }) => (
          <div key={title} className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <Icon className="h-5 w-5 text-brand-600" aria-hidden="true" />
            <h2 className="mt-3 font-semibold text-slate-900">{title}</h2>
            <p className="mt-1 text-sm text-slate-600">{text}</p>
          </div>
        ))}
      </section>
    </div>
  );
}
