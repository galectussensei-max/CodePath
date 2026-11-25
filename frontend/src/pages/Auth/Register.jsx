import { useState } from 'react';
import { Link } from 'react-router-dom';
import { UserPlus } from 'lucide-react';
import Alert from '../../components/Alert.jsx';
import FormField from '../../components/FormField.jsx';
import { useAuth } from '../../context/useAuth.js';
import { getErrorMessage, getFieldErrors } from '../../api/axiosInstance.js';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Same limits as the backend RegisterRequest: name 2-120, valid email, password 8-72.
function validate({ name, email, password, confirmPassword }) {
  const errors = {};
  const trimmedName = name.trim();
  if (trimmedName.length < 2 || trimmedName.length > 120) {
    errors.name = 'Name must be between 2 and 120 characters.';
  }
  if (!email.trim()) {
    errors.email = 'Enter your email address.';
  } else if (!EMAIL_PATTERN.test(email.trim())) {
    errors.email = 'Enter a valid email address.';
  }
  if (password.length < 8 || password.length > 72) {
    errors.password = 'Password must be between 8 and 72 characters.';
  }
  if (confirmPassword !== password) {
    errors.confirmPassword = 'Passwords do not match.';
  }
  return errors;
}

export default function Register() {
  const { register } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;

    const errors = validate({ name, email, password, confirmPassword });
    setFieldErrors(errors);
    setFormError('');
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      // On success GuestRoute sees the new session and sends the new learner to /onboarding.
      await register(name.trim(), email.trim(), password);
    } catch (error) {
      setFieldErrors(getFieldErrors(error));
      setFormError(getErrorMessage(error, 'Registration failed. Please try again.'));
      setSubmitting(false);
    }
  }

  return (
    <section className="mx-auto max-w-md rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
      <h1 className="text-2xl font-bold text-slate-900">Create your account</h1>
      <p className="mt-1 text-sm text-slate-600">
        Next you will set your goals and take a short diagnostic so CodePath can start from where you are.
      </p>

      <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-4">
        {formError && <Alert variant="error">{formError}</Alert>}

        <FormField
          label="Full name"
          name="name"
          value={name}
          onChange={setName}
          error={fieldErrors.name}
          autoComplete="name"
          maxLength={120}
          disabled={submitting}
          autoFocus
        />
        <FormField
          label="Email"
          name="email"
          type="email"
          value={email}
          onChange={setEmail}
          error={fieldErrors.email}
          autoComplete="email"
          disabled={submitting}
        />
        <FormField
          label="Password"
          name="password"
          type="password"
          value={password}
          onChange={setPassword}
          error={fieldErrors.password}
          hint="8 to 72 characters."
          autoComplete="new-password"
          maxLength={72}
          disabled={submitting}
        />
        <FormField
          label="Confirm password"
          name="confirmPassword"
          type="password"
          value={confirmPassword}
          onChange={setConfirmPassword}
          error={fieldErrors.confirmPassword}
          autoComplete="new-password"
          maxLength={72}
          disabled={submitting}
        />

        <button
          type="submit"
          disabled={submitting}
          className="inline-flex w-full items-center justify-center gap-2 rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <UserPlus className="h-4 w-4" aria-hidden="true" />
          {submitting ? 'Creating account...' : 'Create account'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-slate-600">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-brand-600 hover:text-brand-700">
          Log in
        </Link>
      </p>
    </section>
  );
}
