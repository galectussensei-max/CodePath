import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, ChevronDown, Circle, CircleDot, Lock } from 'lucide-react';
import { getErrorMessage } from '../../api/axiosInstance.js';
import { getRoadmap } from '../../api/roadmapService.js';
import DifficultyBadge from '../../components/DifficultyBadge.jsx';
import MasteryBar from '../../components/MasteryBar.jsx';
import { EmptyBlock, ErrorBlock } from '../../components/PageState.jsx';
import Spinner from '../../components/Spinner.jsx';
import { formatMinutes, percent } from '../../utils/insights.js';

const STATE_META = {
  COMPLETED: { label: 'Completed', icon: CheckCircle2, badge: 'bg-emerald-50 text-emerald-700 border-emerald-200', icon_color: 'text-emerald-600' },
  IN_PROGRESS: { label: 'In progress', icon: CircleDot, badge: 'bg-brand-50 text-brand-700 border-brand-100', icon_color: 'text-brand-600' },
  AVAILABLE: { label: 'Available', icon: Circle, badge: 'bg-slate-50 text-slate-700 border-slate-200', icon_color: 'text-slate-500' },
  LOCKED: { label: 'Locked (advisory)', icon: Lock, badge: 'bg-amber-50 text-amber-700 border-amber-200', icon_color: 'text-amber-600' },
};

function StepCard({ step, defaultOpen }) {
  const [open, setOpen] = useState(defaultOpen);
  const meta = STATE_META[step.state] || STATE_META.AVAILABLE;
  const Icon = meta.icon;

  return (
    <li className="relative pl-10">
      <span className="absolute left-0 top-4 flex h-7 w-7 items-center justify-center rounded-full bg-white">
        <Icon className={`h-6 w-6 ${meta.icon_color}`} aria-hidden="true" />
      </span>
      <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <p className="text-xs font-medium text-slate-500">Step {step.order}</p>
            <h3 className="text-base font-semibold text-slate-900">{step.topic}</h3>
          </div>
          <span className={`rounded-full border px-2 py-0.5 text-xs font-medium ${meta.badge}`}>{meta.label}</span>
        </div>
        {step.description && <p className="mt-1 text-sm text-slate-600">{step.description}</p>}

        <div className="mt-3">
          <MasteryBar score={step.masteryScore} status={step.masteryStatus} />
        </div>
        <p className="mt-2 text-xs text-slate-500">
          {step.solvedCount} of {step.totalProblems} problems solved · about {formatMinutes(step.estimatedMinutesRemaining)} remaining
        </p>

        {step.prerequisites && step.prerequisites.length > 0 && (
          <div className="mt-3 flex flex-wrap items-center gap-2 text-xs">
            <span className="text-slate-500">Prerequisites:</span>
            {step.prerequisites.map((pre) => (
              <span
                key={pre.topicId}
                className={`rounded-full border px-2 py-0.5 ${
                  pre.met
                    ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                    : 'border-amber-200 bg-amber-50 text-amber-700'
                }`}
              >
                {pre.met ? 'Met' : 'Not met'}: {pre.topic} ({percent(pre.masteryScore)})
              </span>
            ))}
          </div>
        )}

        {step.lockedReason && (
          <p className="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
            {step.lockedReason} <span className="text-amber-700">This is advice only; you can still practise here.</span>
          </p>
        )}

        {step.problems && step.problems.length > 0 && (
          <div className="mt-3">
            <button
              type="button"
              onClick={() => setOpen((value) => !value)}
              aria-expanded={open}
              className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline"
            >
              <ChevronDown className={`h-4 w-4 transition-transform ${open ? 'rotate-180' : ''}`} aria-hidden="true" />
              {open ? 'Hide problems' : `Show ${step.problems.length} problems`}
            </button>
            {open && (
              <ul className="mt-2 divide-y divide-slate-100 rounded-lg border border-slate-200">
                {step.problems.map((problem) => (
                  <li key={problem.questionId} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                    <div className="flex min-w-0 items-center gap-2">
                      {problem.solved ? (
                        <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600" aria-label="Solved" />
                      ) : (
                        <Circle className="h-4 w-4 shrink-0 text-slate-300" aria-label="Not solved" />
                      )}
                      <Link to={`/workspace/${problem.slug}`} className="truncate font-medium text-slate-800 hover:text-brand-700">
                        {problem.title}
                      </Link>
                      <span className="hidden truncate text-xs text-slate-500 sm:inline">{problem.concept}</span>
                    </div>
                    <DifficultyBadge difficulty={problem.difficulty} />
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </li>
  );
}

export default function Roadmap() {
  const [roadmap, setRoadmap] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setRoadmap(await getRoadmap());
    } catch (err) {
      setError(getErrorMessage(err, 'Could not load your roadmap.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-900">Your learning roadmap</h1>
        <p className="mt-1 text-sm text-slate-600">
          An optional pathway through the topics in prerequisite order. Locked steps are advice, never a block.
        </p>
      </div>

      {loading && <Spinner label="Building your roadmap..." />}
      {error && <ErrorBlock message={error} onRetry={load} />}

      {roadmap && (
        <>
          <div className="mb-6 grid gap-4 sm:grid-cols-3">
            <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Goal</p>
              <p className="mt-1 font-semibold text-slate-900">
                {roadmap.goal?.prepGoal || 'Not set'}
              </p>
              {roadmap.goal?.targetLevel && (
                <p className="text-xs text-slate-500">Target level: {roadmap.goal.targetLevel}</p>
              )}
            </div>
            <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Daily study time</p>
              <p className="mt-1 font-semibold text-slate-900">
                {roadmap.goal?.dailyStudyMinutes ? `${roadmap.goal.dailyStudyMinutes} min` : 'Not set'}
              </p>
            </div>
            <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Estimated time left</p>
              <p className="mt-1 font-semibold text-slate-900">
                {roadmap.estimatedDaysRemaining != null ? `about ${roadmap.estimatedDaysRemaining} day${roadmap.estimatedDaysRemaining === 1 ? '' : 's'}` : '-'}
              </p>
              <p className="text-xs text-slate-500">A rough estimate from your study time</p>
            </div>
          </div>

          {roadmap.steps.length === 0 ? (
            <EmptyBlock>No roadmap steps are available yet.</EmptyBlock>
          ) : (
            <ol className="relative space-y-4 before:absolute before:bottom-4 before:left-[13px] before:top-4 before:w-px before:bg-slate-200">
              {roadmap.steps.map((step) => (
                <StepCard
                  key={step.topicId}
                  step={step}
                  defaultOpen={step.state === 'IN_PROGRESS' || step.state === 'AVAILABLE'}
                />
              ))}
            </ol>
          )}
          {roadmap.note && <p className="mt-4 text-xs text-slate-500">{roadmap.note}</p>}
        </>
      )}
    </div>
  );
}
