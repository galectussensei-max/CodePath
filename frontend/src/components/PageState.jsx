import Alert from './Alert.jsx';

/** Error box with an optional retry button, built on the shared Alert. */
export function ErrorBlock({ message, onRetry }) {
  return (
    <Alert variant="error">
      <p>{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 rounded-md border border-red-300 bg-white px-3 py-1 text-red-700 hover:bg-red-100"
        >
          Try again
        </button>
      )}
    </Alert>
  );
}

export function EmptyBlock({ children }) {
  return (
    <div className="rounded-xl border border-dashed border-slate-300 bg-white p-6 text-center text-sm text-slate-500">
      {children}
    </div>
  );
}
