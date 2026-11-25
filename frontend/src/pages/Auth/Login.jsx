import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { LogIn } from 'lucide-react';
import Alert from '../../components/Alert.jsx';
import FormField from '../../components/FormField.jsx';
import { useAuth } from '../../context/useAuth.js';
import { getErrorMessage, getFieldErrors } from '../../api/axiosInstance.js';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validate({ email, password }) {
  const errors = {};
  if (!email.trim()) {
    errors.email = 'Enter your email address.';
  } else if (!EMAIL_PATTERN.test(email.trim())) {
    errors.email = 'Enter a valid email address.';
  }
  if (!password) {
    errors.password = 'Enter your password.';
  }
  return errors;
}

export default function Login() {
  const { login } = useAuth();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const redirectedFromProtectedPage = Boolean(location.state?.from);

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;

    const errors = validate({ email, password });
    setFieldErrors(errors);
    setFormError('');
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      // On success GuestRoute sees the new session and redirects, so nothing else happens here.
      await login(email.trim(), password);
    } catch (error) {
      setFieldErrors(getFieldErrors(error));
      setFormError(getErrorMessage(error, 'Login failed. Please try again.'));
      setSubmitting(false);
    }
  }

  return (
    <section className="mx-auto max-w-md rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
      <h1 className="text-2xl font-bold text-slate-900">Welcome back</h1>
      <p className="mt-1 text-sm text-slate-600">Log in to continue your DSA practice.</p>

      <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-4">
        {redirectedFromProtectedPage && !formError && <Alert variant="info">Please log in to continue.</Alert>}
        {formError && <Alert variant="error">{formError}</Alert>}

        <FormField
          label="Email"
          name="email"
          type="email"
          value={email}
          onChange={setEmail}
          error={fieldErrors.email}
          autoComplete="email"
          disabled={submitting}
          autoFocus
        />
        <FormField
          label="Password"
          name="password"
          type="password"
          value={password}
          onChange={setPassword}
          error={fieldErrors.password}
          autoComplete="current-password"
          disabled={submitting}
        />

        <button
          type="submit"
          disabled={submitting}
          className="inline-flex w-full items-center justify-center gap-2 rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <LogIn className="h-4 w-4" aria-hidden="true" />
          {submitting ? 'Logging in...' : 'Log in'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-slate-600">
        New to CodePath?{' '}
        <Link to="/register" className="font-medium text-brand-600 hover:text-brand-700">
          Create an account
        </Link>
      </p>
    </section>
  );
}
