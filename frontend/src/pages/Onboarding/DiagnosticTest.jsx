import { useEffect, useMemo, useState } from 'react';
import { ArrowLeft, ArrowRight, CheckCircle2, Send } from 'lucide-react';
import Alert from '../../components/Alert.jsx';
import RichText from '../../components/RichText.jsx';
import Spinner from '../../components/Spinner.jsx';
import { getErrorMessage } from '../../api/axiosInstance.js';
import { getDiagnostic, submitDiagnostic } from '../../api/assessmentService.js';

const DIFFICULTY_STYLES = {
  EASY: 'bg-green-50 text-green-700 ring-green-200',
  MEDIUM: 'bg-amber-50 text-amber-700 ring-amber-200',
  HARD: 'bg-red-50 text-red-700 ring-red-200',
};

/** Drops the seed's boilerplate heading and trailing instruction; the UI already conveys both. */
function cleanPrompt(prompt) {
  return (prompt || '')
    .replace(/^\s*\*\*Diagnostic question[^\n]*\*\*\s*/i, '')
    .replace(/\s*Answer with a single letter[^\n]*\s*$/i, '')
    .trim();
}

/**
 * Step 2 of onboarding: one multiple-choice question at a time. Answers are only sent when every
 * question has one; grading happens on the server, which never reveals the answer key.
 */
export default function DiagnosticTest({ onCompleted, onAlreadyCompleted, onBack }) {
  const [questions, setQuestions] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [answers, setAnswers] = useState({});
  const [index, setIndex] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setQuestions(null);
    setLoadError('');
    getDiagnostic()
      .then((data) => {
        if (cancelled) return;
        if (!Array.isArray(data) || data.length === 0) {
          setLoadError('No diagnostic questions are available right now.');
        } else {
          setQuestions(data);
        }
      })
      .catch((error) => {
        if (!cancelled) setLoadError(getErrorMessage(error, 'Could not load the diagnostic.'));
      });
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  const answeredCount = useMemo(
    () => (questions ? questions.filter((question) => answers[question.id]).length : 0),
    [questions, answers]
  );

  async function handleSubmit() {
    if (submitting || !questions || answeredCount !== questions.length) return;
    setSubmitting(true);
    setSubmitError('');
    try {
      const result = await submitDiagnostic(
        questions.map((question) => ({ questionId: question.id, answer: answers[question.id] }))
      );
      onCompleted(result);
    } catch (error) {
      if (error?.response?.status === 409) {
        // Already graded earlier (for example from another tab): nothing left to do here.
        onAlreadyCompleted();
        return;
      }
      setSubmitError(getErrorMessage(error, 'Could not submit the diagnostic. Please try again.'));
      setSubmitting(false);
    }
  }

  if (loadError) {
    return (
      <div className="space-y-4">
        <Alert variant="error">{loadError}</Alert>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => setReloadKey((key) => key + 1)}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
          >
            Try again
          </button>
          <button
            type="button"
            onClick={onBack}
            className="rounded-md px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100"
          >
            Back to goals
          </button>
        </div>
      </div>
    );
  }

  if (!questions) {
    return <Spinner label="Loading the diagnostic..." />;
  }

  const question = questions[index];
  const isLast = index === questions.length - 1;
  const allAnswered = answeredCount === questions.length;
  const firstUnanswered = questions.findIndex((item) => !answers[item.id]);
  const progressPercent = Math.round((answeredCount / questions.length) * 100);

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-xl font-semibold text-slate-900">Quick diagnostic</h2>
        <p className="mt-1 text-sm text-slate-600">
          {questions.length} multiple-choice questions across the core topics. Answer honestly: it is a starting
          estimate, not a grade, and unsure answers are fine.
        </p>
      </div>

      <div>
        <div className="flex items-center justify-between text-xs text-slate-500">
          <span>
            Question {index + 1} of {questions.length}
          </span>
          <span>
            {answeredCount} of {questions.length} answered
          </span>
        </div>
        <div
          className="mt-1 h-2 overflow-hidden rounded-full bg-slate-200"
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={progressPercent}
          aria-label="Questions answered"
        >
          <div className="h-full rounded-full bg-brand-500 transition-all" style={{ width: `${progressPercent}%` }} />
        </div>
      </div>

      <div className="flex flex-wrap gap-2" aria-label="Question navigator">
        {questions.map((item, itemIndex) => {
          const isCurrent = itemIndex === index;
          const isAnswered = Boolean(answers[item.id]);
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => setIndex(itemIndex)}
              disabled={submitting}
              aria-label={`Question ${itemIndex + 1}${isAnswered ? ', answered' : ', not answered'}`}
              aria-current={isCurrent ? 'step' : undefined}
              className={`h-8 w-8 rounded-md border text-xs font-semibold disabled:opacity-60 ${
                isCurrent
                  ? 'border-brand-600 bg-brand-600 text-white'
                  : isAnswered
                    ? 'border-brand-200 bg-brand-50 text-brand-700'
                    : 'border-slate-300 bg-white text-slate-600 hover:bg-slate-100'
              }`}
            >
              {itemIndex + 1}
            </button>
          );
        })}
      </div>

      <fieldset className="rounded-lg border border-slate-200 p-5" disabled={submitting}>
        <legend className="sr-only">Question {index + 1}</legend>
        <div className="flex flex-wrap items-center gap-2">
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
            {question.topic}
          </span>
          <span
            className={`rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${
              DIFFICULTY_STYLES[question.difficulty] || DIFFICULTY_STYLES.EASY
            }`}
          >
            {question.difficulty.charAt(0) + question.difficulty.slice(1).toLowerCase()}
          </span>
        </div>

        <div className="mt-4">
          <RichText text={cleanPrompt(question.prompt)} />
        </div>

        <div className="mt-5 space-y-2" role="radiogroup" aria-label="Answer options">
          {question.options.map((option) => {
            const selected = answers[question.id] === option.label;
            return (
              <label
                key={option.label}
                className={`flex cursor-pointer items-start gap-3 rounded-md border px-3 py-2.5 text-sm focus-within:ring-2 focus-within:ring-brand-200 ${
                  selected ? 'border-brand-500 bg-brand-50' : 'border-slate-300 bg-white hover:bg-slate-50'
                }`}
              >
                <input
                  type="radio"
                  name={`question-${question.id}`}
                  value={option.label}
                  checked={selected}
                  onChange={() => setAnswers((current) => ({ ...current, [question.id]: option.label }))}
                  className="sr-only"
                />
                <span
                  className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-semibold ${
                    selected ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-600'
                  }`}
                  aria-hidden="true"
                >
                  {option.label}
                </span>
                <span className="pt-0.5 text-slate-800">{option.text}</span>
              </label>
            );
          })}
        </div>
      </fieldset>

      {submitError && <Alert variant="error">{submitError}</Alert>}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex gap-2">
          {index === 0 ? (
            <button
              type="button"
              onClick={onBack}
              disabled={submitting}
              className="inline-flex items-center gap-1 rounded-md px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 disabled:opacity-60"
            >
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              Back to goals
            </button>
          ) : (
            <button
              type="button"
              onClick={() => setIndex((current) => current - 1)}
              disabled={submitting}
              className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-60"
            >
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              Previous
            </button>
          )}
        </div>

        {isLast ? (
          <div className="flex flex-col items-end gap-1">
            <button
              type="button"
              onClick={handleSubmit}
              disabled={!allAnswered || submitting}
              className="inline-flex items-center gap-2 rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {submitting ? 'Grading...' : 'Submit diagnostic'}
              {!submitting && <Send className="h-4 w-4" aria-hidden="true" />}
            </button>
            {!allAnswered && (
              <button
                type="button"
                onClick={() => setIndex(firstUnanswered)}
                disabled={submitting}
                className="text-xs text-brand-600 hover:text-brand-700"
              >
                {questions.length - answeredCount} unanswered: go to question {firstUnanswered + 1}
              </button>
            )}
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setIndex((current) => current + 1)}
            disabled={submitting}
            className="inline-flex items-center gap-1 rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60"
          >
            {answers[question.id] ? 'Next' : 'Skip for now'}
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </button>
        )}
      </div>

      {allAnswered && !isLast && (
        <p className="flex items-center gap-1 text-xs text-green-700">
          <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
          All questions answered. Go to question {questions.length} to submit.
        </p>
      )}
    </div>
  );
}
