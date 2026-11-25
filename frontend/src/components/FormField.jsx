import { useId, useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

const INPUT_BASE =
  'block w-full rounded-md border bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:outline-none focus:ring-2 disabled:bg-slate-100 disabled:text-slate-500';
const INPUT_OK = 'border-slate-300 focus:border-brand-500 focus:ring-brand-100';
const INPUT_ERROR = 'border-red-400 focus:border-red-500 focus:ring-red-100';

/** Labelled text/number/password input with an inline error and an optional hint. */
export default function FormField({
  label,
  value,
  onChange,
  type = 'text',
  error,
  hint,
  autoComplete,
  name,
  disabled,
  placeholder,
  maxLength,
  min,
  max,
  inputMode,
  autoFocus,
}) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  const isPassword = type === 'password';
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [error ? errorId : null, hint && !error ? hintId : null].filter(Boolean).join(' ') || undefined;

  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <div className="relative mt-1">
        <input
          id={id}
          name={name}
          type={isPassword && visible ? 'text' : type}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          autoComplete={autoComplete}
          disabled={disabled}
          placeholder={placeholder}
          maxLength={maxLength}
          min={min}
          max={max}
          inputMode={inputMode}
          autoFocus={autoFocus}
          aria-invalid={error ? 'true' : undefined}
          aria-describedby={describedBy}
          className={`${INPUT_BASE} ${error ? INPUT_ERROR : INPUT_OK} ${isPassword ? 'pr-10' : ''}`}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setVisible((current) => !current)}
            aria-label={visible ? 'Hide password' : 'Show password'}
            className="absolute inset-y-0 right-0 flex items-center px-3 text-slate-500 hover:text-slate-700"
          >
            {visible ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
          </button>
        )}
      </div>
      {hint && !error && (
        <p id={hintId} className="mt-1 text-xs text-slate-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="mt-1 text-xs text-red-600">
          {error}
        </p>
      )}
    </div>
  );
}
