import { CheckCircle2, Clock, Cpu, Loader2, ShieldCheck, XCircle } from 'lucide-react';
import Alert from './Alert.jsx';
import { humanizeEnum } from '../utils/format.js';

const VERDICT_STYLES = {
  ACCEPTED: { box: 'border-green-300 bg-green-50', chip: 'bg-green-600 text-white' },
  WRONG_ANSWER: { box: 'border-red-300 bg-red-50', chip: 'bg-red-600 text-white' },
  RUNTIME_ERROR: { box: 'border-red-300 bg-red-50', chip: 'bg-red-600 text-white' },
  COMPILATION_ERROR: { box: 'border-orange-300 bg-orange-50', chip: 'bg-orange-600 text-white' },
  TIME_LIMIT_EXCEEDED: { box: 'border-orange-300 bg-orange-50', chip: 'bg-orange-600 text-white' },
};

const NEUTRAL = { box: 'border-slate-200 bg-white', chip: 'bg-slate-600 text-white' };

function Pre({ label, text }) {
  if (text === undefined || text === null) {
    return null;
  }
  return (
    <div>
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <pre className="mt-1 max-h-40 overflow-auto rounded bg-slate-900 p-2 font-mono text-xs text-slate-100">
        {text === '' ? '(empty)' : text}
      </pre>
    </div>
  );
}

function CaseRow({ testCase }) {
  const title = testCase.hidden ? `Hidden case ${testCase.index}` : `Sample case ${testCase.index}`;
  const summary = (
    <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
      {testCase.passed ? (
        <CheckCircle2 className="h-4 w-4 text-green-600" aria-label="Passed" />
      ) : (
        <XCircle className="h-4 w-4 text-red-600" aria-label="Failed" />
      )}
      <span className="font-medium text-slate-800">{title}</span>
      <span className={testCase.passed ? 'text-green-700' : 'text-red-700'}>
        {testCase.passed ? 'Passed' : humanizeEnum(testCase.verdict)}
      </span>
      {testCase.timeMs != null && <span className="text-xs text-slate-500">{testCase.timeMs} ms</span>}
      {testCase.memoryKb != null && <span className="text-xs text-slate-500">{testCase.memoryKb} KB</span>}
    </span>
  );

  if (testCase.hidden) {
    return <li className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm">{summary}</li>;
  }
  return (
    <li className="rounded-md border border-slate-200 bg-white text-sm">
      <details>
        <summary className="cursor-pointer px-3 py-2">{summary}</summary>
        <div className="space-y-2 border-t border-slate-100 px-3 py-2">
          <Pre label="Input" text={testCase.input} />
          <Pre label="Expected output" text={testCase.expectedOutput} />
          <Pre label="Your output" text={testCase.actualOutput} />
          <Pre label="Error output" text={testCase.errorOutput} />
        </div>
      </details>
    </li>
  );
}

function MasteryUpdate({ update }) {
  const change = update.change ?? 0;
  const sign = change > 0 ? '+' : '';
  return (
    <div className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm">
      <p className="font-medium text-slate-800">
        Mastery estimate for {update.topicName}: {update.previousScore?.toFixed(1)} &rarr; {update.newScore?.toFixed(1)}{' '}
        <span className={change > 0 ? 'text-green-700' : change < 0 ? 'text-red-700' : 'text-slate-500'}>
          ({sign}
          {change.toFixed(1)})
        </span>
      </p>
      {Array.isArray(update.factors) && update.factors.length > 0 && (
        <ul className="mt-1 list-disc space-y-0.5 pl-5 text-xs text-slate-600">
          {update.factors.map((factor) => (
            <li key={factor}>{factor}</li>
          ))}
        </ul>
      )}
      {update.note && <p className="mt-1 text-xs text-slate-500">{update.note}</p>}
    </div>
  );
}

/**
 * "Verified Execution Verdict": only what Judge0 reported. AI guidance is never shown in this box.
 * @param {{result: object|null, busy: 'run'|'submit'|null, error: string}} props
 */
export default function VerdictBox({ result, busy, error }) {
  const style = result ? VERDICT_STYLES[result.verdict] || NEUTRAL : NEUTRAL;

  return (
    <section aria-label="Verified Execution Verdict" className={`rounded-lg border p-3 ${style.box}`}>
      <header className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
          <ShieldCheck className="h-4 w-4 text-green-700" aria-hidden="true" />
          Verified Execution Verdict
        </h2>
        <span className="text-xs text-slate-500">Judged by Judge0 in an isolated sandbox</span>
      </header>

      <div className="mt-3 space-y-3">
        {busy && (
          <p className="flex items-center gap-2 text-sm text-slate-600" role="status">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            {busy === 'submit'
              ? 'Judging against all test cases. This can take a few seconds...'
              : 'Running against the sample cases...'}
          </p>
        )}

        {error && !busy && (
          <Alert variant="error" title="No verdict">
            {error} Nothing was saved and your mastery estimate was not changed.
          </Alert>
        )}

        {!busy && !error && !result && (
          <p className="text-sm text-slate-600">
            Run your code to check it against the sample cases, or Submit to be judged on all cases, including hidden
            ones.
          </p>
        )}

        {result && !busy && (
          <>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
              <span className={`rounded-md px-2.5 py-1 text-sm font-semibold ${style.chip}`}>
                {humanizeEnum(result.verdict)}
              </span>
              <span className="text-sm text-slate-700">
                {result.mode === 'SUBMIT' ? 'Submission' : 'Run'}: {result.passedCount} / {result.totalCount} cases passed
              </span>
              {result.executionTimeMs != null && (
                <span className="inline-flex items-center gap-1 text-sm text-slate-600">
                  <Clock className="h-4 w-4" aria-hidden="true" />
                  {result.executionTimeMs} ms
                </span>
              )}
              {result.memoryUsedKb != null && (
                <span className="inline-flex items-center gap-1 text-sm text-slate-600">
                  <Cpu className="h-4 w-4" aria-hidden="true" />
                  {result.memoryUsedKb} KB
                </span>
              )}
              {result.mode === 'RUN' && (
                <span className="text-xs text-slate-500">Sample cases only; not saved, mastery unchanged.</span>
              )}
            </div>

            {result.compileOutput && <Pre label="Compiler output" text={result.compileOutput} />}

            {Array.isArray(result.testCases) && result.testCases.length > 0 && (
              <ul className="space-y-2">
                {result.testCases.map((testCase) => (
                  <CaseRow key={testCase.index} testCase={testCase} />
                ))}
              </ul>
            )}
            {result.testCases?.some((testCase) => testCase.hidden) && (
              <p className="text-xs text-slate-500">
                Hidden cases only report pass or fail, so their inputs and answers are never revealed.
              </p>
            )}

            {result.masteryUpdate && <MasteryUpdate update={result.masteryUpdate} />}
          </>
        )}
      </div>
    </section>
  );
}
