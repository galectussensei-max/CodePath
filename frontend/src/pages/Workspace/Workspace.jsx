import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Play, RotateCcw, Send, Timer } from 'lucide-react';
import { getErrorMessage } from '../../api/axiosInstance.js';
import { getQuestionBySlug } from '../../api/questionService.js';
import { getSubmissionHistory, runCode, submitCode } from '../../api/submissionService.js';
import Alert from '../../components/Alert.jsx';
import DifficultyBadge from '../../components/DifficultyBadge.jsx';
import DiscussionPanel from '../../components/DiscussionPanel.jsx';
import MonacoWrapper from '../../components/MonacoWrapper.jsx';
import RichText from '../../components/RichText.jsx';
import SafeguardedAIBox from '../../components/SafeguardedAIBox.jsx';
import Spinner from '../../components/Spinner.jsx';
import VerdictBox from '../../components/VerdictBox.jsx';
import { formatDateTime, formatDuration, humanizeEnum } from '../../utils/format.js';

const TABS = [
  { id: 'problem', label: 'Problem' },
  { id: 'discussion', label: 'Discussion' },
  { id: 'ai', label: 'AI Assistant' },
];

// Unsent code is kept in this browser only, as a convenience. It is never the source of truth.
const draftKey = (slug) => `codepath:draft:${slug}`;

function readDraft(slug) {
  try {
    return window.localStorage.getItem(draftKey(slug));
  } catch {
    return null;
  }
}

function writeDraft(slug, code) {
  try {
    window.localStorage.setItem(draftKey(slug), code);
  } catch {
    /* storage unavailable: the editor still works, the draft just is not kept */
  }
}

function ElapsedTimer({ startedAt }) {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    const id = window.setInterval(() => setSeconds((Date.now() - startedAt) / 1000), 1000);
    return () => window.clearInterval(id);
  }, [startedAt]);
  return (
    <span className="inline-flex items-center gap-1 text-xs text-slate-500" title="Time spent on this problem in this visit; it is sent with a submission">
      <Timer className="h-3.5 w-3.5" aria-hidden="true" />
      {formatDuration(seconds)}
    </span>
  );
}

function ProblemPane({ question }) {
  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-bold text-slate-900">{question.title}</h1>
        <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-slate-600">
          <DifficultyBadge difficulty={question.difficulty} />
          <span>
            {question.topic} &middot; {question.concept}
          </span>
          {question.placementTags?.map((tag) => (
            <span key={tag} className="rounded bg-brand-50 px-1.5 py-0.5 text-xs font-medium text-brand-700">
              {tag}
            </span>
          ))}
        </div>
      </div>

      <RichText text={question.description} />

      {question.constraints && (
        <div>
          <h2 className="text-sm font-semibold text-slate-900">Constraints</h2>
          <pre className="mt-1 whitespace-pre-wrap rounded-md bg-slate-100 p-3 font-mono text-xs text-slate-800">{question.constraints}</pre>
        </div>
      )}

      <p className="text-xs text-slate-500">
        Limits: {question.timeLimitMs} ms, {Math.round(question.memoryLimitKb / 1024)} MB. Read from standard input and
        print to standard output in a <code className="font-mono">public class Main</code>.
      </p>

      <div>
        <h2 className="text-sm font-semibold text-slate-900">Sample cases</h2>
        {question.sampleTestCases?.length ? (
          <ul className="mt-2 space-y-3">
            {question.sampleTestCases.map((sample, index) => (
              <li key={sample.id} className="rounded-md border border-slate-200 p-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Sample {index + 1}</p>
                <p className="mt-2 text-xs text-slate-500">Input</p>
                <pre className="overflow-x-auto rounded bg-slate-900 p-2 font-mono text-xs text-slate-100">{sample.input || '(empty)'}</pre>
                <p className="mt-2 text-xs text-slate-500">Expected output</p>
                <pre className="overflow-x-auto rounded bg-slate-900 p-2 font-mono text-xs text-slate-100">{sample.expectedOutput}</pre>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-1 text-sm text-slate-600">This problem has no sample cases.</p>
        )}
        <p className="mt-2 text-xs text-slate-500">Submitting also checks hidden cases that are never shown.</p>
      </div>
    </div>
  );
}

function SubmissionHistory({ history, onLoad }) {
  if (history.length === 0) {
    return null;
  }
  return (
    <details className="rounded-lg border border-slate-200 bg-white p-3">
      <summary className="cursor-pointer text-sm font-semibold text-slate-800">Your submissions ({history.length})</summary>
      <ul className="mt-2 divide-y divide-slate-100">
        {history.map((entry) => (
          <li key={entry.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2 text-sm">
            <span className={`font-medium ${entry.verdict === 'ACCEPTED' ? 'text-green-700' : 'text-red-700'}`}>
              {humanizeEnum(entry.verdict)}
            </span>
            {entry.executionTimeMs != null && <span className="text-xs text-slate-500">{entry.executionTimeMs} ms</span>}
            {entry.memoryUsedKb != null && <span className="text-xs text-slate-500">{entry.memoryUsedKb} KB</span>}
            <span className="text-xs text-slate-500">{formatDateTime(entry.createdAt)}</span>
            <button type="button" onClick={() => onLoad(entry)} className="ml-auto text-xs font-medium text-brand-700 hover:underline">
              Load code
            </button>
          </li>
        ))}
      </ul>
    </details>
  );
}

function WorkspaceInner({ slug }) {
  const [question, setQuestion] = useState(null);
  const [loadError, setLoadError] = useState(null);
  const [code, setCode] = useState('');
  const [tab, setTab] = useState('problem');
  const [visited, setVisited] = useState({ problem: true });
  const [busy, setBusy] = useState(null); // 'run' | 'submit' | null
  const [result, setResult] = useState(null);
  const [runError, setRunError] = useState('');
  const [history, setHistory] = useState([]);
  const startedAt = useRef(Date.now());

  useEffect(() => {
    let cancelled = false;
    getQuestionBySlug(slug)
      .then((data) => {
        if (cancelled) return;
        setQuestion(data);
        setCode(readDraft(slug) ?? data.starterCodeJava ?? '');
      })
      .catch((e) => {
        if (!cancelled) {
          setLoadError({
            notFound: e?.response?.status === 404,
            message: getErrorMessage(e, 'Could not load this problem.'),
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [slug]);

  const refreshHistory = useCallback(async (questionId) => {
    try {
      setHistory(await getSubmissionHistory(questionId));
    } catch {
      /* history is a convenience; the workspace works without it */
    }
  }, []);

  useEffect(() => {
    if (question) {
      refreshHistory(question.id);
    }
  }, [question, refreshHistory]);

  function selectTab(id) {
    setTab(id);
    setVisited((current) => ({ ...current, [id]: true }));
  }

  function handleCodeChange(next) {
    setCode(next);
    writeDraft(slug, next);
  }

  function handleReset() {
    if (window.confirm('Reset the editor to the starter code? Your current code will be replaced.')) {
      handleCodeChange(question.starterCodeJava ?? '');
    }
  }

  function handleLoadSubmission(entry) {
    if (window.confirm('Replace the editor contents with this submission?')) {
      handleCodeChange(entry.sourceCode ?? '');
    }
  }

  async function execute(mode) {
    if (busy || !question) return;
    if (!code.trim()) {
      setRunError('Write some code first.');
      return;
    }
    setBusy(mode);
    setRunError('');
    setResult(null); // never leave an old verdict next to a new run
    try {
      if (mode === 'run') {
        setResult(await runCode({ questionId: question.id, sourceCode: code }));
      } else {
        const timeTakenSeconds = Math.min(86400, Math.round((Date.now() - startedAt.current) / 1000));
        setResult(await submitCode({ questionId: question.id, sourceCode: code, timeTakenSeconds }));
        refreshHistory(question.id);
      }
    } catch (e) {
      setRunError(getErrorMessage(e, 'The judge could not process your code.'));
    } finally {
      setBusy(null);
    }
  }

  if (loadError) {
    return (
      <div className="space-y-3">
        <Alert variant="error" title={loadError.notFound ? 'Problem not found' : 'Could not open the problem'}>
          {loadError.notFound ? 'That problem does not exist.' : loadError.message}
        </Alert>
        <Link to="/problems" className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back to problems
        </Link>
      </div>
    );
  }
  if (!question) {
    return <Spinner label="Loading problem..." />;
  }

  const latestAccepted = history.find((entry) => entry.verdict === 'ACCEPTED');
  const lastVerdict = result?.verdict ?? history[0]?.verdict;

  return (
    <div className="grid gap-4 lg:h-[calc(100vh-6.5rem)] lg:grid-cols-2">
      {/* Left pane */}
      <section className="flex min-h-[420px] flex-col rounded-xl border border-slate-200 bg-white shadow-sm lg:min-h-0">
        <div className="flex items-center gap-1 border-b border-slate-200 px-2 pt-2" role="tablist">
          <Link to="/problems" className="mr-2 inline-flex items-center gap-1 px-2 pb-2 text-xs text-slate-500 hover:text-brand-700">
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" /> Problems
          </Link>
          {TABS.map((entry) => (
            <button
              key={entry.id}
              type="button"
              role="tab"
              aria-selected={tab === entry.id}
              onClick={() => selectTab(entry.id)}
              className={`-mb-px border-b-2 px-3 pb-2 text-sm font-medium ${
                tab === entry.id ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-600 hover:text-slate-900'
              }`}
            >
              {entry.label}
            </button>
          ))}
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto p-4">
          <div className={tab === 'problem' ? '' : 'hidden'}>
            <ProblemPane question={question} />
          </div>
          {visited.discussion && (
            <div className={tab === 'discussion' ? '' : 'hidden'}>
              <DiscussionPanel questionId={question.id} />
            </div>
          )}
          {visited.ai && (
            <div className={tab === 'ai' ? '' : 'hidden'}>
              <SafeguardedAIBox
                questionId={question.id}
                sourceCode={code}
                lastVerdict={lastVerdict}
                acceptedSubmissionId={latestAccepted?.id ?? null}
              />
            </div>
          )}
        </div>
      </section>

      {/* Right pane: editor on top, verified verdict below */}
      <div className="flex min-h-0 flex-col gap-3">
        <section className="flex min-h-[360px] flex-1 flex-col overflow-hidden rounded-xl border border-slate-300 bg-white shadow-sm">
          <div className="flex flex-wrap items-center gap-2 border-b border-slate-200 bg-slate-50 px-3 py-2">
            <span className="text-sm font-semibold text-slate-800">Java</span>
            <ElapsedTimer startedAt={startedAt.current} />
            <div className="ml-auto flex items-center gap-2">
              <button
                type="button"
                onClick={handleReset}
                className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-700 hover:bg-slate-100"
              >
                <RotateCcw className="h-4 w-4" aria-hidden="true" /> Reset
              </button>
              <button
                type="button"
                onClick={() => execute('run')}
                disabled={busy !== null}
                className="inline-flex items-center gap-1 rounded-md border border-brand-600 bg-white px-3 py-1.5 text-sm font-semibold text-brand-700 hover:bg-brand-50 disabled:opacity-60"
              >
                <Play className="h-4 w-4" aria-hidden="true" /> Run
              </button>
              <button
                type="button"
                onClick={() => execute('submit')}
                disabled={busy !== null}
                className="inline-flex items-center gap-1 rounded-md bg-brand-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60"
              >
                <Send className="h-4 w-4" aria-hidden="true" /> Submit
              </button>
            </div>
          </div>
          <div className="min-h-[300px] flex-1">
            <MonacoWrapper value={code} onChange={handleCodeChange} />
          </div>
        </section>

        <div className="space-y-3 lg:max-h-[42%] lg:overflow-y-auto">
          <VerdictBox result={result} busy={busy} error={runError} />
          <SubmissionHistory history={history} onLoad={handleLoadSubmission} />
        </div>
      </div>
    </div>
  );
}

/** Task 7.2: split-screen coding workspace. Keyed by slug so moving between problems resets all state. */
export default function Workspace() {
  const { slug } = useParams();
  return <WorkspaceInner key={slug} slug={slug} />;
}
