import MasteryBadge, { masteryBarClass } from './MasteryBadge.jsx';
import { percent } from '../utils/insights.js';

/** A mastery score bar. The score is a rule-based estimate, never a measurement of ability. */
export default function MasteryBar({ score = 0, status = 'UNASSESSED', showLabel = true }) {
  const width = Math.max(0, Math.min(100, Number(score) || 0));

  return (
    <div className="w-full">
      <div
        className="h-2 w-full overflow-hidden rounded-full bg-slate-100"
        role="progressbar"
        aria-valuenow={Math.round(width)}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label="Estimated mastery"
      >
        <div className={`h-full rounded-full ${masteryBarClass(status)}`} style={{ width: `${width}%` }} />
      </div>
      {showLabel && (
        <div className="mt-1 flex items-center justify-between text-xs">
          <span className="text-slate-600">{percent(width)} estimated</span>
          <MasteryBadge status={status} />
        </div>
      )}
    </div>
  );
}
