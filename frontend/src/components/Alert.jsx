import { AlertCircle, CheckCircle2, Info } from 'lucide-react';

const VARIANTS = {
  error: { icon: AlertCircle, classes: 'border-red-200 bg-red-50 text-red-800' },
  info: { icon: Info, classes: 'border-sky-200 bg-sky-50 text-sky-800' },
  success: { icon: CheckCircle2, classes: 'border-green-200 bg-green-50 text-green-800' },
};

export default function Alert({ variant = 'info', title, children }) {
  const { icon: Icon, classes } = VARIANTS[variant] || VARIANTS.info;
  return (
    <div
      role={variant === 'error' ? 'alert' : 'status'}
      className={`flex gap-2 rounded-md border px-3 py-2 text-sm ${classes}`}
    >
      <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <div>
        {title && <p className="font-semibold">{title}</p>}
        <div>{children}</div>
      </div>
    </div>
  );
}
