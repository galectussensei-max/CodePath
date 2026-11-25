const STYLES = {
  EASY: 'bg-green-50 text-green-700 ring-green-200',
  MEDIUM: 'bg-amber-50 text-amber-700 ring-amber-200',
  HARD: 'bg-red-50 text-red-700 ring-red-200',
};

const LABELS = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' };

export default function DifficultyBadge({ difficulty }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${
        STYLES[difficulty] || 'bg-slate-100 text-slate-600 ring-slate-200'
      }`}
    >
      {LABELS[difficulty] || difficulty}
    </span>
  );
}
