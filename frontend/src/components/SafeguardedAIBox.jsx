import { useState } from 'react';
import { BadgeCheck, Bot, Lightbulb, Loader2, Sparkles, TriangleAlert } from 'lucide-react';
import { getErrorMessage } from '../api/axiosInstance.js';
import { requestHint, requestReview } from '../api/aiService.js';
import Alert from './Alert.jsx';
import RichText from './RichText.jsx';
import { formatDateTime } from '../utils/format.js';

export const AI_BOX_TITLE = 'AI Assistant (Unverified Guidance — Verify with Judge)';
const MAX_HINT_SOURCE_CHARS = 20000;

const LEVELS = [
  { level: 1, short: 'Nudge', title: 'Level 1 · Conceptual nudge' },
  { level: 2, short: 'Logic', title: 'Level 2 · Logic and edge cases' },
  { level: 3, short: 'Steps', title: 'Level 3 · Algorithm steps (no code)' },
  { level: 4, short: 'Skeleton', title: 'Level 4 · Partial skeleton with blanks' },
];

function titleFor(guidance) {
  if (guidance.interactionType === 'AC_REVIEW') {
    return 'Review of your accepted solution';
  }
  return LEVELS.find((entry) => entry.level === guidance.hintLevel)?.title ?? 'AI guidance';
}

function GuidanceCard({ guidance }) {
  return (
    <article className="rounded-lg border border-violet-200 bg-white p-3">
      <header className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-violet-900">{titleFor(guidance)}</h3>
        <span className="text-xs text-slate-500">{formatDateTime(guidance.createdAt)}</span>
      </header>
      <div className="mt-2">
        <RichText text={guidance.content} preserveLines />
      </div>
      <footer className="mt-3 flex flex-wrap items-center gap-2 border-t border-violet-100 pt-2 text-xs text-amber-800">
        <TriangleAlert className="h-3.5 w-3.5" aria-hidden="true" />
        <span>{guidance.label || AI_BOX_TITLE}</span>
        <span className="rounded bg-amber-100 px-1.5 py-0.5 font-medium">Not verified by the judge</span>
      </footer>
    </article>
  );
}

/**
 * AI guidance, visually and structurally separate from the Verified Execution Verdict box.
 * Nothing in here is judged: the judge's verdict is the only source of truth.
 *
 * @param {{questionId:number, sourceCode:string, lastVerdict?:string, acceptedSubmissionId?:number|null}} props
 */
export default function SafeguardedAIBox({ questionId, sourceCode, lastVerdict, acceptedSubmissionId }) {
  const [items, setItems] = useState([]); // newest first
  const [busy, setBusy] = useState(null); // 1-4 for a hint level, 'review' for the review
  const [error, setError] = useState('');
  const [hintsUsed, setHintsUsed] = useState(null);

  const obtainedLevels = new Set(items.filter((item) => item.hintLevel).map((item) => item.hintLevel));
  const nextLevel = Math.min(4, Math.max(0, ...obtainedLevels) + 1);

  function addItem(guidance) {
    setItems((current) => [guidance, ...current]);
    if (typeof guidance.hintsUsedForQuestion === 'number') {
      setHintsUsed(guidance.hintsUsedForQuestion);
    }
  }

  async function askHint(level) {
    if (busy) {
      return;
    }
    if (sourceCode.length > MAX_HINT_SOURCE_CHARS) {
      setError(`Your code is longer than ${MAX_HINT_SOURCE_CHARS.toLocaleString()} characters, so hints cannot analyse it.`);
      return;
    }
    setBusy(level);
    setError('');
    try {
      addItem(await requestHint({ questionId, sourceCode, verdict: lastVerdict, hintLevel: level }));
    } catch (e) {
      setError(getErrorMessage(e, 'The AI assistant could not produce a hint. No hint was counted.'));
    } finally {
      setBusy(null);
    }
  }

  async function askReview() {
    if (busy) {
      return;
    }
    setBusy('review');
    setError('');
    try {
      addItem(await requestReview({ questionId, submissionId: acceptedSubmissionId }));
    } catch (e) {
      setError(getErrorMessage(e, 'The AI assistant could not review your solution.'));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section
      aria-label="AI Assistant (Unverified Guidance)"
      className="rounded-lg border-2 border-dashed border-violet-300 bg-violet-50/60 p-3"
    >
      <header className="flex flex-wrap items-center gap-2">
        <Bot className="h-4 w-4 text-violet-700" aria-hidden="true" />
        <h2 className="text-sm font-semibold text-violet-900">{AI_BOX_TITLE}</h2>
      </header>
      <p className="mt-2 flex items-start gap-2 rounded-md bg-amber-50 px-2 py-1.5 text-xs text-amber-900 ring-1 ring-inset ring-amber-200">
        <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <span>
          AI can be wrong. It never replaces the judge: run or submit your code to get a verified verdict. Hints are
          staged and never give a full solution.
        </span>
      </p>

      <div className="mt-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-600">Ask for a hint</p>
        <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
          {LEVELS.map(({ level, short }) => {
            const obtained = obtainedLevels.has(level);
            const recommended = level === nextLevel;
            return (
              <button
                key={level}
                type="button"
                onClick={() => askHint(level)}
                disabled={busy !== null}
                className={`flex items-center justify-center gap-1 rounded-md border px-2 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-60 ${
                  recommended
                    ? 'border-violet-600 bg-violet-600 text-white hover:bg-violet-700'
                    : 'border-violet-300 bg-white text-violet-800 hover:bg-violet-100'
                }`}
              >
                {busy === level ? (
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                ) : obtained ? (
                  <BadgeCheck className="h-4 w-4" aria-hidden="true" />
                ) : (
                  <Lightbulb className="h-4 w-4" aria-hidden="true" />
                )}
                L{level} {short}
              </button>
            );
          })}
        </div>
        <p className="mt-2 text-xs text-slate-600">
          Go in order: each level needs the one before it. Every hint you request is counted
          {hintsUsed !== null ? ` (${hintsUsed} used on this problem)` : ''}, and it lowers the mastery credit you get
          if you solve the problem, so try the earlier levels first.
        </p>
      </div>

      <div className="mt-3 border-t border-violet-200 pt-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-600">After you are accepted</p>
        <button
          type="button"
          onClick={askReview}
          disabled={busy !== null || !acceptedSubmissionId}
          className="mt-2 inline-flex items-center gap-2 rounded-md border border-violet-300 bg-white px-3 py-2 text-sm font-medium text-violet-800 hover:bg-violet-100 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {busy === 'review' ? (
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          ) : (
            <Sparkles className="h-4 w-4" aria-hidden="true" />
          )}
          Analyze Accepted Solution
        </button>
        {!acceptedSubmissionId && (
          <p className="mt-1 text-xs text-slate-600">Available once you have an Accepted submission for this problem.</p>
        )}
      </div>

      {error && (
        <div className="mt-3">
          <Alert variant="error">{error}</Alert>
        </div>
      )}

      <div className="mt-3 space-y-3">
        {items.length === 0 && !busy && (
          <p className="text-sm text-slate-600">No guidance requested yet. Hints appear here, newest first.</p>
        )}
        {busy && (
          <p className="flex items-center gap-2 text-sm text-violet-800" role="status">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            Asking the AI assistant...
          </p>
        )}
        {items.map((guidance) => (
          <GuidanceCard key={guidance.interactionId} guidance={guidance} />
        ))}
      </div>
    </section>
  );
}
