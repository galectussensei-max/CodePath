import { useId } from 'react';

/**
 * Labelled <select> over a list of strings. A stored value that is not one of the presets (for
 * example a free-text goal saved earlier) is kept as an extra option so it is never silently lost.
 */
export default function SelectField({ label, value, onChange, options, placeholder = 'Select...', error, disabled }) {
  const id = useId();
  const errorId = `${id}-error`;
  const hasExtra = value && !options.includes(value);

  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <select
        id={id}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={error ? errorId : undefined}
        className={`mt-1 block w-full rounded-md border bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:outline-none focus:ring-2 disabled:bg-slate-100 ${
          error
            ? 'border-red-400 focus:border-red-500 focus:ring-red-100'
            : 'border-slate-300 focus:border-brand-500 focus:ring-brand-100'
        }`}
      >
        <option value="">{placeholder}</option>
        {hasExtra && <option value={value}>{value}</option>}
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
      {error && (
        <p id={errorId} className="mt-1 text-xs text-red-600">
          {error}
        </p>
      )}
    </div>
  );
}
