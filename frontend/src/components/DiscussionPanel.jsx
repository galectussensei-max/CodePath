import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, MessageSquare, Plus, ThumbsUp } from 'lucide-react';
import { getErrorMessage } from '../api/axiosInstance.js';
import {
  addComment,
  createDiscussion,
  getDiscussion,
  listDiscussions,
  upvoteDiscussion,
} from '../api/discussionService.js';
import Alert from './Alert.jsx';
import Spinner from './Spinner.jsx';
import { formatDateTime } from '../utils/format.js';

const inputClasses =
  'w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500';

function NewThreadForm({ questionId, onCreated, onCancel }) {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(event) {
    event.preventDefault();
    if (title.trim().length < 3) {
      setError('Title must be at least 3 characters.');
      return;
    }
    if (!content.trim()) {
      setError('Write something in the body.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      onCreated(await createDiscussion(questionId, { title: title.trim(), content: content.trim() }));
    } catch (e) {
      setError(getErrorMessage(e, 'Could not post your thread.'));
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <button type="button" onClick={onCancel} className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-brand-700">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back to threads
      </button>
      {error && <Alert variant="error">{error}</Alert>}
      <div>
        <label htmlFor="thread-title" className="text-sm font-medium text-slate-700">
          Title
        </label>
        <input id="thread-title" className={`${inputClasses} mt-1`} value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />
      </div>
      <div>
        <label htmlFor="thread-content" className="text-sm font-medium text-slate-700">
          Your question or approach
        </label>
        <textarea
          id="thread-content"
          rows={6}
          className={`${inputClasses} mt-1`}
          value={content}
          maxLength={5000}
          onChange={(e) => setContent(e.target.value)}
        />
        <p className="mt-1 text-xs text-slate-500">Please do not paste a complete solution.</p>
      </div>
      <button
        type="submit"
        disabled={saving}
        className="rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60"
      >
        {saving ? 'Posting...' : 'Post thread'}
      </button>
    </form>
  );
}

function Thread({ discussionId, onBack, onChanged }) {
  const [thread, setThread] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [comment, setComment] = useState('');
  const [posting, setPosting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getDiscussion(discussionId)
      .then((data) => !cancelled && setThread(data))
      .catch((e) => !cancelled && setError(getErrorMessage(e, 'Could not load this thread.')));
    return () => {
      cancelled = true;
    };
  }, [discussionId]);

  async function handleUpvote() {
    setNotice('');
    try {
      const updated = await upvoteDiscussion(discussionId);
      setThread((current) => ({ ...current, upvotes: updated.upvotes }));
      onChanged();
    } catch (e) {
      setNotice(getErrorMessage(e, 'Could not upvote.'));
    }
  }

  async function handleComment(event) {
    event.preventDefault();
    if (!comment.trim()) {
      return;
    }
    setPosting(true);
    setNotice('');
    try {
      const created = await addComment(discussionId, comment.trim());
      setThread((current) => ({ ...current, comments: [...(current.comments ?? []), created] }));
      setComment('');
      onChanged();
    } catch (e) {
      setNotice(getErrorMessage(e, 'Could not post your comment.'));
    } finally {
      setPosting(false);
    }
  }

  return (
    <div className="space-y-3">
      <button type="button" onClick={onBack} className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-brand-700">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back to threads
      </button>
      {error && <Alert variant="error">{error}</Alert>}
      {!thread && !error && <Spinner label="Loading thread..." />}
      {thread && (
        <>
          <article className="rounded-lg border border-slate-200 bg-white p-3">
            <h3 className="text-base font-semibold text-slate-900">{thread.title}</h3>
            <p className="text-xs text-slate-500">
              {thread.authorName} &middot; {formatDateTime(thread.createdAt)}
            </p>
            <p className="mt-2 whitespace-pre-wrap break-words text-sm text-slate-800">{thread.content}</p>
            <button
              type="button"
              onClick={handleUpvote}
              className="mt-3 inline-flex items-center gap-1 rounded-md border border-slate-300 px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100"
            >
              <ThumbsUp className="h-3.5 w-3.5" aria-hidden="true" /> Upvote ({thread.upvotes})
            </button>
          </article>

          {notice && <Alert variant="info">{notice}</Alert>}

          <h4 className="text-sm font-semibold text-slate-800">Comments ({thread.comments?.length ?? 0})</h4>
          <ul className="space-y-2">
            {(thread.comments ?? []).map((item) => (
              <li key={item.id} className="rounded-md border border-slate-200 bg-white p-2">
                <p className="text-xs text-slate-500">
                  {item.authorName} &middot; {formatDateTime(item.createdAt)}
                </p>
                <p className="mt-1 whitespace-pre-wrap break-words text-sm text-slate-800">{item.content}</p>
              </li>
            ))}
          </ul>

          <form onSubmit={handleComment} className="space-y-2">
            <label htmlFor="new-comment" className="sr-only">
              Add a comment
            </label>
            <textarea
              id="new-comment"
              rows={3}
              className={inputClasses}
              placeholder="Add a comment"
              value={comment}
              maxLength={2000}
              onChange={(e) => setComment(e.target.value)}
            />
            <button
              type="submit"
              disabled={posting || !comment.trim()}
              className="rounded-md bg-brand-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60"
            >
              {posting ? 'Posting...' : 'Comment'}
            </button>
          </form>
        </>
      )}
    </div>
  );
}

/** Problem-specific discussion threads for the workspace. */
export default function DiscussionPanel({ questionId }) {
  const [view, setView] = useState({ name: 'list' }); // list | new | thread
  const [threads, setThreads] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      setThreads(await listDiscussions(questionId));
      setError('');
    } catch (e) {
      setError(getErrorMessage(e, 'Could not load the discussion.'));
    }
  }, [questionId]);

  useEffect(() => {
    load();
  }, [load]);

  if (view.name === 'new') {
    return (
      <NewThreadForm
        questionId={questionId}
        onCancel={() => setView({ name: 'list' })}
        onCreated={(created) => {
          load();
          setView({ name: 'thread', id: created.id });
        }}
      />
    );
  }
  if (view.name === 'thread') {
    return <Thread discussionId={view.id} onBack={() => setView({ name: 'list' })} onChanged={load} />;
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
          <MessageSquare className="h-4 w-4" aria-hidden="true" /> Discussion
        </h2>
        <button
          type="button"
          onClick={() => setView({ name: 'new' })}
          className="inline-flex items-center gap-1 rounded-md bg-brand-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-brand-700"
        >
          <Plus className="h-4 w-4" aria-hidden="true" /> New thread
        </button>
      </div>
      {error && <Alert variant="error">{error}</Alert>}
      {!threads && !error && <Spinner label="Loading threads..." />}
      {threads && threads.length === 0 && <p className="text-sm text-slate-600">No threads yet. Start the first one.</p>}
      <ul className="space-y-2">
        {(threads ?? []).map((thread) => (
          <li key={thread.id}>
            <button
              type="button"
              onClick={() => setView({ name: 'thread', id: thread.id })}
              className="w-full rounded-md border border-slate-200 bg-white p-3 text-left hover:border-brand-500"
            >
              <p className="text-sm font-medium text-slate-900">{thread.title}</p>
              <p className="mt-1 text-xs text-slate-500">
                {thread.authorName} &middot; {thread.upvotes} upvotes &middot; {thread.commentCount} comments &middot;{' '}
                {formatDateTime(thread.createdAt)}
              </p>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
