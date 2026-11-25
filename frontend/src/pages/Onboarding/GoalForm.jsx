import { useEffect, useState } from 'react';
import { ArrowRight } from 'lucide-react';
import Alert from '../../components/Alert.jsx';
import FormField from '../../components/FormField.jsx';
import SelectField from '../../components/SelectField.jsx';
import Spinner from '../../components/Spinner.jsx';
import { getErrorMessage, getFieldErrors } from '../../api/axiosInstance.js';
import { getProfile, updateProfile } from '../../api/profileService.js';

const PROGRAMMING_BACKGROUND = [
  'Beginner: just started programming',
  'Comfortable with Java',
  'Comfortable with another language (Python, C++, JavaScript...)',
  'Experienced developer',
];
const DSA_EXPERIENCE = [
  'New to DSA',
  'Know the basics (arrays, strings, recursion)',
  'Have solved some problems on practice sites',
  'Comfortable with Medium problems',
];
const PREP_GOALS = [
  'Campus placements',
  'Product-based company interviews',
  'Service-based company interviews',
  'Strengthen my DSA fundamentals',
];
const TARGET_LEVELS = ['Beginner', 'Intermediate', 'Advanced'];
const MINUTE_PRESETS = [30, 60, 90, 120];

// Same bounds as the backend UpdateProfileRequest.
const MIN_MINUTES = 10;
const MAX_MINUTES = 720;

function validate(form) {
  const errors = {};
  if (!form.prepGoal.trim()) {
    errors.prepGoal = 'Choose what you are preparing for.';
  }
  const minutes = Number(form.dailyStudyMinutes);
  if (form.dailyStudyMinutes === '' || !Number.isInteger(minutes) || minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
    errors.dailyStudyMinutes = `Enter a whole number of minutes from ${MIN_MINUTES} to ${MAX_MINUTES}.`;
  }
  return errors;
}

/** Step 1 of onboarding: goals and daily study time, saved with PUT /api/users/profile. */
export default function GoalForm({ onSaved }) {
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [form, setForm] = useState({
    programmingBackground: '',
    dsaExperience: '',
    prepGoal: '',
    targetLevel: '',
    dailyStudyMinutes: '60',
  });
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  // Pre-fill from any profile saved earlier (for example when the user left halfway through).
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError('');
    getProfile()
      .then((profile) => {
        if (cancelled) return;
        setForm({
          programmingBackground: profile.programmingBackground || '',
          dsaExperience: profile.dsaExperience || '',
          prepGoal: profile.prepGoal || '',
          targetLevel: profile.targetLevel || '',
          dailyStudyMinutes: String(profile.dailyStudyMinutes || 60),
        });
      })
      .catch((error) => {
        if (!cancelled) setLoadError(getErrorMessage(error, 'Could not load your profile.'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  function setField(name) {
    return (value) => setForm((current) => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (saving) return;

    const errors = validate(form);
    setFieldErrors(errors);
    setFormError('');
    if (Object.keys(errors).length > 0) return;

    setSaving(true);
    try {
      await updateProfile({
        programmingBackground: form.programmingBackground,
        dsaExperience: form.dsaExperience,
        prepGoal: form.prepGoal.trim(),
        targetLevel: form.targetLevel,
        dailyStudyMinutes: Number(form.dailyStudyMinutes),
      });
      onSaved();
    } catch (error) {
      setFieldErrors(getFieldErrors(error));
      setFormError(getErrorMessage(error, 'Could not save your goals. Please try again.'));
      setSaving(false);
    }
  }

  if (loading) {
    return <Spinner label="Loading your profile..." />;
  }

  if (loadError) {
    return (
      <div className="space-y-4">
        <Alert variant="error">{loadError}</Alert>
        <button
          type="button"
          onClick={() => setReloadKey((key) => key + 1)}
          className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
        >
          Try again
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-5">
      <div>
        <h2 className="text-xl font-semibold text-slate-900">Tell us about your goals</h2>
        <p className="mt-1 text-sm text-slate-600">
          This shapes how much CodePath plans for each day. Only the goal and study time are required.
        </p>
      </div>

      {formError && <Alert variant="error">{formError}</Alert>}

      <SelectField
        label="What are you preparing for?"
        value={form.prepGoal}
        onChange={setField('prepGoal')}
        options={PREP_GOALS}
        placeholder="Choose a goal"
        error={fieldErrors.prepGoal}
        disabled={saving}
      />
      <SelectField
        label="Programming background (optional)"
        value={form.programmingBackground}
        onChange={setField('programmingBackground')}
        options={PROGRAMMING_BACKGROUND}
        error={fieldErrors.programmingBackground}
        disabled={saving}
      />
      <SelectField
        label="DSA experience (optional)"
        value={form.dsaExperience}
        onChange={setField('dsaExperience')}
        options={DSA_EXPERIENCE}
        error={fieldErrors.dsaExperience}
        disabled={saving}
      />
      <SelectField
        label="Target level (optional)"
        value={form.targetLevel}
        onChange={setField('targetLevel')}
        options={TARGET_LEVELS}
        error={fieldErrors.targetLevel}
        disabled={saving}
      />

      <div>
        <FormField
          label="Daily study time (minutes)"
          name="dailyStudyMinutes"
          type="number"
          inputMode="numeric"
          value={form.dailyStudyMinutes}
          onChange={setField('dailyStudyMinutes')}
          min={MIN_MINUTES}
          max={MAX_MINUTES}
          error={fieldErrors.dailyStudyMinutes}
          hint={`Between ${MIN_MINUTES} and ${MAX_MINUTES}. Used to estimate how long your roadmap will take.`}
          disabled={saving}
        />
        <div className="mt-2 flex flex-wrap gap-2">
          {MINUTE_PRESETS.map((minutes) => (
            <button
              key={minutes}
              type="button"
              disabled={saving}
              onClick={() => setField('dailyStudyMinutes')(String(minutes))}
              className={`rounded-full border px-3 py-1 text-xs font-medium disabled:opacity-60 ${
                form.dailyStudyMinutes === String(minutes)
                  ? 'border-brand-500 bg-brand-50 text-brand-700'
                  : 'border-slate-300 text-slate-600 hover:bg-slate-100'
              }`}
            >
              {minutes} min
            </button>
          ))}
        </div>
      </div>

      <button
        type="submit"
        disabled={saving}
        className="inline-flex items-center gap-2 rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {saving ? 'Saving...' : 'Save and continue'}
        {!saving && <ArrowRight className="h-4 w-4" aria-hidden="true" />}
      </button>
    </form>
  );
}
