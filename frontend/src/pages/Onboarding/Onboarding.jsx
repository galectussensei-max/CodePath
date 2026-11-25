import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { BarChart3, Check, ClipboardList, Target } from 'lucide-react';
import { useAuth } from '../../context/useAuth.js';
import DiagnosticTest from './DiagnosticTest.jsx';
import GoalForm from './GoalForm.jsx';
import MasteryReveal from './MasteryReveal.jsx';

const STEPS = [
  { key: 'goals', label: 'Goals', icon: Target },
  { key: 'diagnostic', label: 'Diagnostic', icon: ClipboardList },
  { key: 'results', label: 'Your starting point', icon: BarChart3 },
];

function Stepper({ current }) {
  const currentIndex = STEPS.findIndex((step) => step.key === current);
  return (
    <ol className="flex items-center gap-2 text-sm" aria-label="Onboarding progress">
      {STEPS.map((step, stepIndex) => {
        const done = stepIndex < currentIndex;
        const active = stepIndex === currentIndex;
        const Icon = done ? Check : step.icon;
        return (
          <li key={step.key} className="flex items-center gap-2" aria-current={active ? 'step' : undefined}>
            <span
              className={`flex h-7 w-7 items-center justify-center rounded-full ${
                active ? 'bg-brand-600 text-white' : done ? 'bg-brand-100 text-brand-700' : 'bg-slate-200 text-slate-500'
              }`}
            >
              <Icon className="h-4 w-4" aria-hidden="true" />
            </span>
            <span className={`hidden sm:inline ${active ? 'font-semibold text-slate-900' : 'text-slate-500'}`}>
              {step.label}
            </span>
            {stepIndex < STEPS.length - 1 && <span className="h-px w-6 bg-slate-300" aria-hidden="true" />}
          </li>
        );
      })}
    </ol>
  );
}

/**
 * Onboarding flow: goals -> diagnostic -> initial mastery reveal.
 * A user who already finished the diagnostic when this page mounted is sent to the dashboard. That
 * check is captured once, so finishing the diagnostic here (which flips diagnosticCompleted) does not
 * bounce the user away from their own results screen.
 */
export default function Onboarding() {
  const { user, updateUser } = useAuth();
  const navigate = useNavigate();
  const [alreadyCompleted] = useState(() => Boolean(user?.diagnosticCompleted));
  const [stage, setStage] = useState('goals');
  const [result, setResult] = useState(null);

  if (alreadyCompleted) {
    return <Navigate to="/dashboard" replace />;
  }

  function handleDiagnosticCompleted(diagnosticResult) {
    updateUser({ diagnosticCompleted: true });
    setResult(diagnosticResult);
    setStage('results');
  }

  function handleAlreadyCompleted() {
    updateUser({ diagnosticCompleted: true });
    navigate('/dashboard', { replace: true });
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Let&rsquo;s set up CodePath, {user.name.split(' ')[0]}</h1>
        <div className="mt-4">
          <Stepper current={stage} />
        </div>
      </div>

      <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        {stage === 'goals' && <GoalForm onSaved={() => setStage('diagnostic')} />}
        {stage === 'diagnostic' && (
          <DiagnosticTest
            onCompleted={handleDiagnosticCompleted}
            onAlreadyCompleted={handleAlreadyCompleted}
            onBack={() => setStage('goals')}
          />
        )}
        {stage === 'results' && result && (
          <MasteryReveal result={result} onContinue={() => navigate('/dashboard', { replace: true })} />
        )}
      </section>
    </div>
  );
}
