import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { getAnalyticsSummary } from '../../api/insightsService.js';
import { getErrorMessage } from '../../api/axiosInstance.js';
import MasteryBar from '../../components/MasteryBar.jsx';
import { EmptyBlock, ErrorBlock } from '../../components/PageState.jsx';
import Spinner from '../../components/Spinner.jsx';
import { humanizeEnum } from '../../utils/format.js';
import { VERDICT_COLORS, VERDICT_LABELS, formatDate, percent } from '../../utils/insights.js';

function Card({ title, subtitle, children }) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="text-base font-semibold text-slate-900">{title}</h2>
      {subtitle && <p className="mb-3 text-xs text-slate-500">{subtitle}</p>}
      {children}
    </section>
  );
}

function Stat({ label, value }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-bold text-slate-900">{value}</p>
    </div>
  );
}

export default function Analytics() {
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setSummary(await getAnalyticsSummary());
    } catch (err) {
      setError(getErrorMessage(err, 'Could not load your analytics.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const masteryData = useMemo(
    () =>
      (summary?.topicMastery || []).map((point) => ({
        topic: point.topic,
        score: Math.round(point.masteryScore),
      })),
    [summary]
  );

  const verdictData = useMemo(
    () =>
      (summary?.verdictDistribution || [])
        .filter((item) => item.count > 0)
        .map((item) => ({
          key: item.verdict,
          name: VERDICT_LABELS[item.verdict] || humanizeEnum(item.verdict),
          value: item.count,
        })),
    [summary]
  );

  const difficultyData = useMemo(
    () =>
      (summary?.difficultyBreakdown || []).map((item) => ({
        difficulty: humanizeEnum(item.difficulty),
        Solved: item.solved,
        'Attempted, not solved': Math.max(0, item.attempted - item.solved),
        'Not attempted': Math.max(0, item.totalProblems - item.attempted),
      })),
    [summary]
  );

  const overview = summary?.overview;

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-900">Analytics</h1>
        <p className="mt-1 text-sm text-slate-600">
          Mastery values are rule-based estimates from your submissions, not measurements of ability.
        </p>
      </div>

      {loading && <Spinner label="Loading your analytics..." />}
      {error && <ErrorBlock message={error} onRetry={load} />}

      {summary && (
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Stat label="Problems solved" value={`${overview.problemsSolved} / ${overview.totalProblems}`} />
            <Stat label="Submissions" value={overview.totalSubmissions} />
            <Stat
              label="Acceptance rate"
              value={overview.totalSubmissions > 0 ? percent(overview.acceptanceRatePercent) : '-'}
            />
            <Stat
              label="Avg accepted runtime"
              value={overview.averageAcceptedRuntimeMs != null ? `${Math.round(overview.averageAcceptedRuntimeMs)} ms` : '-'}
            />
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <Card title="Topic mastery" subtitle="Estimated score per topic, 0 to 100">
              {masteryData.length === 0 ? (
                <EmptyBlock>No topics to show yet.</EmptyBlock>
              ) : (
                <div className="h-72 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    {masteryData.length >= 3 ? (
                      <RadarChart data={masteryData} outerRadius="75%">
                        <PolarGrid />
                        <PolarAngleAxis dataKey="topic" tick={{ fontSize: 11 }} />
                        <PolarRadiusAxis domain={[0, 100]} tick={{ fontSize: 10 }} />
                        <Radar name="Mastery" dataKey="score" stroke="#4f46e5" fill="#6366f1" fillOpacity={0.4} />
                        <Tooltip formatter={(value) => [`${value}%`, 'Mastery']} />
                      </RadarChart>
                    ) : (
                      <BarChart data={masteryData}>
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis dataKey="topic" tick={{ fontSize: 11 }} />
                        <YAxis domain={[0, 100]} tick={{ fontSize: 11 }} />
                        <Tooltip formatter={(value) => [`${value}%`, 'Mastery']} />
                        <Bar dataKey="score" fill="#6366f1" />
                      </BarChart>
                    )}
                  </ResponsiveContainer>
                </div>
              )}
            </Card>

            <Card title="Verdict distribution" subtitle="Judge0 verdicts across all your submissions">
              {verdictData.length === 0 ? (
                <EmptyBlock>Submit a solution to see your verdicts here.</EmptyBlock>
              ) : (
                <div className="h-72 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie data={verdictData} dataKey="value" nameKey="name" innerRadius="45%" outerRadius="75%" paddingAngle={2}>
                        {verdictData.map((entry) => (
                          <Cell key={entry.key} fill={VERDICT_COLORS[entry.key] || '#94a3b8'} />
                        ))}
                      </Pie>
                      <Tooltip />
                      <Legend />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Card>
          </div>

          <Card title="Difficulty progress" subtitle="Problems solved, attempted and untouched at each difficulty">
            {difficultyData.length === 0 ? (
              <EmptyBlock>No problems found.</EmptyBlock>
            ) : (
              <div className="h-64 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={difficultyData} layout="vertical" margin={{ left: 16 }}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
                    <YAxis type="category" dataKey="difficulty" tick={{ fontSize: 12 }} width={70} />
                    <Tooltip />
                    <Legend />
                    <Bar dataKey="Solved" stackId="d" fill="#16a34a" />
                    <Bar dataKey="Attempted, not solved" stackId="d" fill="#f59e0b" />
                    <Bar dataKey="Not attempted" stackId="d" fill="#e2e8f0" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </Card>

          <div className="grid gap-6 lg:grid-cols-2">
            <Card title="Topic details" subtitle="Where each estimate comes from">
              <ul className="space-y-4">
                {summary.topicMastery.map((point) => (
                  <li key={point.topicId}>
                    <div className="mb-1 flex items-baseline justify-between text-sm">
                      <span className="font-medium text-slate-800">{point.topic}</span>
                      <span className="text-xs text-slate-500">
                        {point.solvedCount}/{point.totalProblems} solved · {point.attemptsCount} attempts · last {formatDate(point.lastPracticedAt)}
                      </span>
                    </div>
                    <MasteryBar score={point.masteryScore} status={point.status} />
                  </li>
                ))}
              </ul>
            </Card>

            <Card title="Recurring errors" subtitle="Failing verdicts that keep happening in the same topic">
              {summary.recurringErrors.length === 0 ? (
                <EmptyBlock>No recurring error patterns found. Nice.</EmptyBlock>
              ) : (
                <ul className="space-y-3">
                  {summary.recurringErrors.map((item) => (
                    <li
                      key={`${item.topicId}-${item.verdict}`}
                      className="rounded-lg border border-slate-200 p-3 text-sm"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium text-slate-800">{item.topic}</span>
                        <span
                          className="rounded-full px-2 py-0.5 text-xs font-medium text-white"
                          style={{ backgroundColor: VERDICT_COLORS[item.verdict] || '#64748b' }}
                        >
                          {VERDICT_LABELS[item.verdict] || humanizeEnum(item.verdict)} x{item.count}
                        </span>
                      </div>
                      <p className="mt-1 text-slate-600">{item.description}</p>
                      <p className="mt-1 text-xs text-slate-500">{percent(item.sharePercent)} of your submissions in this topic</p>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>

          {summary.note && <p className="text-xs text-slate-500">{summary.note}</p>}
        </div>
      )}
    </div>
  );
}
