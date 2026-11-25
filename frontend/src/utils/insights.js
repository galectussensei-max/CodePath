export const VERDICT_LABELS = {
  PENDING: 'Pending',
  ACCEPTED: 'Accepted',
  WRONG_ANSWER: 'Wrong Answer',
  COMPILATION_ERROR: 'Compilation Error',
  RUNTIME_ERROR: 'Runtime Error',
  TIME_LIMIT_EXCEEDED: 'Time Limit Exceeded',
};

export const VERDICT_COLORS = {
  PENDING: '#94a3b8',
  ACCEPTED: '#16a34a',
  WRONG_ANSWER: '#dc2626',
  COMPILATION_ERROR: '#f59e0b',
  RUNTIME_ERROR: '#9333ea',
  TIME_LIMIT_EXCEEDED: '#0ea5e9',
};

export const TRIGGER_LABELS = {
  SCHEDULED: 'Scheduled review',
  HIGH_ERROR_RATE: 'High error rate',
  BOTH: 'Scheduled + high error rate',
};

export function formatDate(iso) {
  if (!iso) return 'never';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return 'never';
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatMinutes(minutes) {
  if (minutes == null) return '-';
  if (minutes <= 0) return 'done';
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}

export function percent(value) {
  return `${Math.round(Number(value) || 0)}%`;
}
