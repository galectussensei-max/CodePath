import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Lock, RotateCcw, Sparkles } from 'lucide-react';
import { getAnalyticsSummary, getRecommendations, getRevisionQueue } from '../../api/insightsService.js';
import { getErrorMessage } from '../../api/axiosInstance.js';
import { useAuth } from '../../context/useAuth.js';
import DifficultyBadge from '../../components/DifficultyBadge.jsx';
import MasteryBar from '../../components/MasteryBar.jsx';
import { EmptyBlock, ErrorBlock } from '../../components/PageState.jsx';
import Spinner from '../../components/Spinner.jsx';
import { TRIGGER_LABELS, formatDate, percent } from '../../utils/insights.js';

function StatCard({ label, value, hint }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-bold text-slate-900">{value}</p>
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </div>
  );
}

export default function Dashboard() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [recs, setRecs] = useState(null);
  const [revision, setRevision] = useState(null);
  const [summary, setSummary] = useState(null);
  const [errors, setErrors] = useState({});

  // The three panels load independently: one failing endpoint must not blank the whole dashboard.
  const load = useCallback(async () => {
    setLoading(true);
    const [recResult, revResult, sumResult] = await Promise.allSettled([
      getRecommendations(5),
      getRevisionQueue(),
      getAnalyticsSummary(),
    ]);
    const nextErrors = {};
    if (recResult.status === 'fulfilled') setRecs(recResult.value);
    else nextErrors.recs = getErrorMessage(recResult.reason, 'Could not load recommendations.');
    if (revResult.status === 'fulfilled') setRevision(revResult.value);
    else nextErrors.revision = getErrorMessage(revResult.reason, 'Could not load the revision queue.');
    if (sumResult.status === 'fulfilled') setSummary(sumResult.value);
    else nextErrors.summary = getErrorMessage(sumResult.reason, 'Could not load your stats.');
    setErrors(nextErrors);
    setLoading(false);
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const overview = summary?.overview;

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-900">Welcome back, {user?.name}</h1>
        <p className="mt-1 text-sm text-slate-600">
          Your next steps are chosen by transparent rules from your mastery estimates, with the reason shown for every pick.
        </p>
      </div>

      {loading && !recs && !revision && !summary ? (
        <Spinner label="Loading your dashboard..." />
      ) : (
        <>
          <section aria-label="Quick stats" className="mb-8">
            {errors.summary && <ErrorBlock message={errors.summary} onRetry={load} />}
            {overview && (
              <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
                <StatCard
                  label="Problems solved"
                  value={`${overview.problemsSolved} / ${overview.totalProblems}`}
                  hint={`${overview.problemsAttempted} attempted`}
                />
                <StatCard
                  label="Submissions"
                  value={overview.totalSubmissions}
                  hint={`${overview.acceptedSubmissions} accepted`}
                />
                <StatCard
                  label="Acceptance rate"
                  value={overview.totalSubmissions > 0 ? percent(overview.acceptanceRatePercent) : '-'}
                  hint="Accepted / all submissions"
                />
                <StatCard
                  label="Revision due"
                  value={revision ? revision.dueCount : '-'}
                  hint={revision && revision.dueCount === 0 ? 'Nothing is due' : 'Topics to revisit'}
                />
              </div>
            )}
          </section>

          <div className="grid gap-8 lg:grid-cols-5">
            <section aria-label="Recommended next problems" className="lg:col-span-3">
              <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold text-slate-900">
                <Sparkles className="h-5 w-5 text-brand-600" aria-hidden="true" />
                Recommended next problems
              </h2>
              {errors.recs && <ErrorBlock message={errors.recs} onRetry={load} />}
              {recs && recs.recommendations.length === 0 && (
                <EmptyBlock>{recs.note || 'No recommendations yet. Solve a few problems and check back.'}</EmptyBlock>
              )}
              {recs && recs.recommendations.length > 0 && (
                <ol className="space-y-3">
                  {recs.recommendations.map((item) => (
                    <li key={item.questionId} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="text-xs font-medium text-slate-500">
                            #{item.rank} · {item.topic} · {item.concept}
                          </p>
                          <Link
                            to={`/workspace/${item.slug}`}
                            className="mt-0.5 inline-flex items-center gap-1 text-base font-semibold text-slate-900 hover:text-brand-700"
                          >
                            {item.title}
                            <ArrowRight className="h-4 w-4" aria-hidden="true" />
                          </Link>
                        </div>
                        <DifficultyBadge difficulty={item.difficulty} />
                      </div>
                      <p className="mt-3 rounded-md bg-brand-50 px-3 py-2 text-sm text-brand-700">
                        <span className="font-semibold">Why this: </span>
                        {item.reason}
                      </p>
                      {item.placementTags && item.placementTags.length > 0 && (
                        <div className="mt-2 flex flex-wrap gap-1">
                          {item.placementTags.map((tag) => (
                            <span key={tag} className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
                              {tag}
                            </span>
                          ))}
                        </div>
                      )}
                    </li>
                  ))}
                </ol>
              )}
              {recs && recs.note && recs.recommendations.length > 0 && (
                <p className="mt-3 text-xs text-slate-500">{recs.note}</p>
              )}
              {recs && recs.lockedTopics && recs.lockedTopics.length > 0 && (
                <details className="mt-4 rounded-xl border border-slate-200 bg-white p-4 text-sm">
                  <summary className="flex cursor-pointer items-center gap-2 font-medium text-slate-700">
                    <Lock className="h-4 w-4" aria-hidden="true" />
                    {recs.lockedTopics.length} topic{recs.lockedTopics.length === 1 ? '' : 's'} not recommended yet
                  </summary>
                  <ul className="mt-3 space-y-2">
                    {recs.lockedTopics.map((topic) => (
                      <li key={topic.topicId}>
                        <span className="font-medium text-slate-800">{topic.topic}: </span>
                        <span className="text-slate-600">{topic.reason}</span>
                      </li>
                    ))}
                  </ul>
                </details>
              )}
            </section>

            <section aria-label="Revision due queue" className="lg:col-span-2">
              <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold text-slate-900">
                <RotateCcw className="h-5 w-5 text-amber-600" aria-hidden="true" />
                Revision due queue
              </h2>
              {errors.revision && <ErrorBlock message={errors.revision} onRetry={load} />}
              {revision && revision.items.length === 0 && (
                <EmptyBlock>{revision.note || 'Nothing needs revision right now.'}</EmptyBlock>
              )}
              {revision && revision.items.length > 0 && (
                <ul className="space-y-3">
                  {revision.items.map((item) => (
                    <li key={item.topicId} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                      <div className="flex items-center justify-between gap-2">
                        <p className="font-semibold text-slate-900">{item.topic}</p>
                        <span className="rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700">
                          {TRIGGER_LABELS[item.trigger] || item.trigger}
                        </span>
                      </div>
                      <div className="mt-2">
                        <MasteryBar score={item.masteryScore} status={item.status} />
                      </div>
                      <p className="mt-2 text-sm text-slate-600">{item.reason}</p>
                      <p className="mt-1 text-xs text-slate-500">
                        Last practised {formatDate(item.lastPracticedAt)}
                        {item.daysOverdue > 0 ? ` · ${item.daysOverdue} day${item.daysOverdue === 1 ? '' : 's'} overdue` : ''}
                        {item.gradedSubmissions > 0 ? ` · error rate ${percent(item.errorRate * 100)}` : ''}
                      </p>
                      <Link to="/problems" className="mt-2 inline-block text-sm font-medium text-brand-700 hover:underline">
                        Open the problem bank
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  );
}
