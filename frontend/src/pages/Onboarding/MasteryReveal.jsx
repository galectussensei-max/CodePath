import { ArrowRight } from 'lucide-react';
import Alert from '../../components/Alert.jsx';
import MasteryBadge, { masteryBarClass } from '../../components/MasteryBadge.jsx';

// Status thresholds used by the backend (MasteryStatus): WEAK below 40, STRONG from 70.
const WEAK_BELOW = 40;
const STRONG_FROM = 70;

function clampPercent(score) {
  return Math.max(0, Math.min(100, Number(score) || 0));
}

/** Step 3 of onboarding: the initial, rule-based mastery estimate per topic. */
export default function MasteryReveal({ result, onContinue }) {
  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-xl font-semibold text-slate-900">Your starting point</h2>
        <p className="mt-1 text-sm text-slate-600">
          You answered <strong>{result.correctCount}</strong> of <strong>{result.totalQuestions}</strong> diagnostic
          questions correctly. Here is where each topic starts.
        </p>
      </div>

      <Alert variant="info" title="These are estimates">
        {result.note ||
          'Initial mastery values are rule-based estimates from a short diagnostic, not measurements of true ability.'}
      </Alert>

      <ul className="space-y-3">
        {result.topics.map((topic) => {
          const assessed = topic.total > 0;
          const percent = clampPercent(topic.masteryScore);
          return (
            <li key={topic.topicId} className="rounded-lg border border-slate-200 bg-white p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="font-medium text-slate-900">{topic.topicName}</p>
                  <p className="text-xs text-slate-500">
                    {assessed
                      ? `${topic.correct} of ${topic.total} diagnostic question${topic.total === 1 ? '' : 's'} correct`
                      : 'No diagnostic question for this topic'}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {assessed && <span className="text-sm font-semibold text-slate-800">{percent.toFixed(1)}%</span>}
                  <MasteryBadge status={assessed ? topic.status : 'UNASSESSED'} />
                </div>
              </div>

              <div
                className="relative mt-3 h-2.5 rounded-full bg-slate-100"
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={Math.round(percent)}
                aria-label={`${topic.topicName} mastery estimate`}
              >
                <div
                  className={`h-full rounded-full ${masteryBarClass(assessed ? topic.status : 'UNASSESSED')}`}
                  style={{ width: `${assessed ? percent : 0}%` }}
                />
                <span className="absolute inset-y-0 w-px bg-slate-400/60" style={{ left: `${WEAK_BELOW}%` }} aria-hidden="true" />
                <span className="absolute inset-y-0 w-px bg-slate-400/60" style={{ left: `${STRONG_FROM}%` }} aria-hidden="true" />
              </div>
            </li>
          );
        })}
      </ul>

      <p className="text-xs text-slate-500">
        Weak is below {WEAK_BELOW}%, Developing is {WEAK_BELOW}% to {STRONG_FROM - 1}%, and Strong is {STRONG_FROM}% or
        more. The faint lines on each bar mark these cut-offs.
      </p>

      <button
        type="button"
        onClick={onContinue}
        className="inline-flex items-center gap-2 rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700"
      >
        Go to dashboard
        <ArrowRight className="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  );
}
