const STATUS_STYLES = {
  UNASSESSED: { label: 'Not assessed', classes: 'bg-slate-100 text-slate-600 ring-slate-200' },
  WEAK: { label: 'Weak', classes: 'bg-red-50 text-red-700 ring-red-200' },
  DEVELOPING: { label: 'Developing', classes: 'bg-amber-50 text-amber-700 ring-amber-200' },
  STRONG: { label: 'Strong', classes: 'bg-green-50 text-green-700 ring-green-200' },
};

/** Topic mastery status chip (UNASSESSED / WEAK / DEVELOPING / STRONG). */
export default function MasteryBadge({ status }) {
  const style = STATUS_STYLES[status] || STATUS_STYLES.UNASSESSED;
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${style.classes}`}>
      {style.label}
    </span>
  );
}

/** Bar colour that matches the status chip. */
export function masteryBarClass(status) {
  switch (status) {
    case 'WEAK':
      return 'bg-red-500';
    case 'DEVELOPING':
      return 'bg-amber-500';
    case 'STRONG':
      return 'bg-green-500';
    default:
      return 'bg-slate-300';
  }
}
