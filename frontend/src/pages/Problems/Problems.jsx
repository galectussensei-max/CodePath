import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Circle } from 'lucide-react';
import { getErrorMessage } from '../../api/axiosInstance.js';
import { listQuestions } from '../../api/questionService.js';
import { getSolvedSlugs } from '../../api/roadmapService.js';
import Alert from '../../components/Alert.jsx';
import DifficultyBadge from '../../components/DifficultyBadge.jsx';
import Spinner from '../../components/Spinner.jsx';

const DIFFICULTIES = [
  { value: 'EASY', label: 'Easy' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HARD', label: 'Hard' },
];

const selectClasses =
  'w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 disabled:bg-slate-100 disabled:text-slate-400';

function Filter({ id, label, children }) {
  return (
    <div>
      <label htmlFor={id} className="block text-xs font-semibold uppercase tracking-wide text-slate-500">
        {label}
      </label>
      <div className="mt-1">{children}</div>
    </div>
  );
}

/** Task 7.1: the filterable problem bank. All filtering happens in the browser on the full (small) list. */
export default function Problems() {
  const [questions, setQuestions] = useState(null);
  const [solvedSlugs, setSolvedSlugs] = useState(null); // null = status unknown
  const [error, setError] = useState('');
  const [topicId, setTopicId] = useState('');
  const [conceptId, setConceptId] = useState('');
  const [difficulty, setDifficulty] = useState('');
  const [status, setStatus] = useState('');

  useEffect(() => {
    let cancelled = false;
    listQuestions()
      .then((list) => !cancelled && setQuestions(list))
      .catch((e) => !cancelled && setError(getErrorMessage(e, 'Could not load the problem bank.')));
    // Solved status comes from the roadmap. If it fails, the list still works and Status is disabled.
    getSolvedSlugs()
      .then((slugs) => !cancelled && setSolvedSlugs(slugs))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  const topics = useMemo(() => {
    const byId = new Map();
    (questions ?? []).forEach((q) => byId.set(q.topicId, q.topic));
    return [...byId.entries()].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name));
  }, [questions]);

  const concepts = useMemo(() => {
    const byId = new Map();
    (questions ?? [])
      .filter((q) => !topicId || String(q.topicId) === topicId)
      .forEach((q) => byId.set(q.conceptId, q.concept));
    return [...byId.entries()].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name));
  }, [questions, topicId]);

  const visible = useMemo(() => {
    return (questions ?? []).filter((q) => {
      if (topicId && String(q.topicId) !== topicId) return false;
      if (conceptId && String(q.conceptId) !== conceptId) return false;
      if (difficulty && q.difficulty !== difficulty) return false;
      if (status && solvedSlugs) {
        const solved = solvedSlugs.has(q.slug);
        if (status === 'SOLVED' && !solved) return false;
        if (status === 'UNSOLVED' && solved) return false;
      }
      return true;
    });
  }, [questions, solvedSlugs, topicId, conceptId, difficulty, status]);

  const solvedTotal = solvedSlugs && questions ? questions.filter((q) => solvedSlugs.has(q.slug)).length : null;
  const filtersActive = Boolean(topicId || conceptId || difficulty || status);

  function handleTopicChange(value) {
    setTopicId(value);
    setConceptId(''); // a concept belongs to one topic
  }

  function clearFilters() {
    setTopicId('');
    setConceptId('');
    setDifficulty('');
    setStatus('');
  }

  return (
    <section>
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Problems</h1>
          <p className="mt-1 text-sm text-slate-600">Practice in Java. Pick a problem to open the coding workspace.</p>
        </div>
        {solvedTotal !== null && (
          <p className="text-sm font-medium text-slate-700">
            {solvedTotal} of {questions.length} solved
          </p>
        )}
      </div>

      {error && (
        <div className="mt-4">
          <Alert variant="error">{error}</Alert>
        </div>
      )}
      {!questions && !error && <Spinner label="Loading problems..." />}

      {questions && (
        <>
          <div className="mt-4 grid gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:grid-cols-2 lg:grid-cols-4">
            <Filter id="filter-topic" label="Topic">
              <select id="filter-topic" className={selectClasses} value={topicId} onChange={(e) => handleTopicChange(e.target.value)}>
                <option value="">All topics</option>
                {topics.map((t) => (
                  <option key={t.id} value={String(t.id)}>
                    {t.name}
                  </option>
                ))}
              </select>
            </Filter>
            <Filter id="filter-concept" label="Concept">
              <select id="filter-concept" className={selectClasses} value={conceptId} onChange={(e) => setConceptId(e.target.value)}>
                <option value="">All concepts</option>
                {concepts.map((c) => (
                  <option key={c.id} value={String(c.id)}>
                    {c.name}
                  </option>
                ))}
              </select>
            </Filter>
            <Filter id="filter-difficulty" label="Difficulty">
              <select id="filter-difficulty" className={selectClasses} value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
                <option value="">Any difficulty</option>
                {DIFFICULTIES.map((d) => (
                  <option key={d.value} value={d.value}>
                    {d.label}
                  </option>
                ))}
              </select>
            </Filter>
            <Filter id="filter-status" label="Status">
              <select
                id="filter-status"
                className={selectClasses}
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                disabled={!solvedSlugs}
              >
                <option value="">Any status</option>
                <option value="SOLVED">Solved</option>
                <option value="UNSOLVED">Unsolved</option>
              </select>
            </Filter>
          </div>

          <div className="mt-2 flex items-center justify-between text-sm text-slate-600">
            <span>
              Showing {visible.length} of {questions.length}
            </span>
            {filtersActive && (
              <button type="button" onClick={clearFilters} className="font-medium text-brand-700 hover:underline">
                Clear filters
              </button>
            )}
          </div>

          {visible.length === 0 ? (
            <p className="mt-6 rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-600">
              No problems match these filters.
            </p>
          ) : (
            <ul className="mt-3 divide-y divide-slate-200 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
              {visible.map((q) => {
                const solved = solvedSlugs?.has(q.slug);
                return (
                  <li key={q.id}>
                    <Link to={`/workspace/${q.slug}`} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-3 hover:bg-slate-50">
                      {solvedSlugs &&
                        (solved ? (
                          <CheckCircle2 className="h-5 w-5 shrink-0 text-green-600" aria-label="Solved" />
                        ) : (
                          <Circle className="h-5 w-5 shrink-0 text-slate-300" aria-label="Not solved yet" />
                        ))}
                      <span className="min-w-0 flex-1 font-medium text-slate-900">{q.title}</span>
                      <span className="text-sm text-slate-600">
                        {q.topic} &middot; {q.concept}
                      </span>
                      <DifficultyBadge difficulty={q.difficulty} />
                      {q.placementTags?.length > 0 && (
                        <span className="flex flex-wrap gap-1">
                          {q.placementTags.map((tag) => (
                            <span key={tag} className="rounded bg-brand-50 px-1.5 py-0.5 text-xs font-medium text-brand-700">
                              {tag}
                            </span>
                          ))}
                        </span>
                      )}
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}
          {!solvedSlugs && (
            <p className="mt-3 text-xs text-slate-500">Solved status is unavailable right now, so the Status filter is disabled.</p>
          )}
        </>
      )}
    </section>
  );
}
