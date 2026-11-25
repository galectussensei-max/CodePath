/** "24 Nov 2026, 11:26" in the viewer's locale, or '' for a missing/invalid value. */
export function formatDateTime(value) {
  if (!value) {
    return '';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return date.toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

/** 125 -> "2:05". */
export function formatDuration(totalSeconds) {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(seconds / 60);
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
}

/** Title case for enum names: TIME_LIMIT_EXCEEDED -> "Time limit exceeded". */
export function humanizeEnum(value) {
  if (!value) {
    return '';
  }
  const text = String(value).replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}
