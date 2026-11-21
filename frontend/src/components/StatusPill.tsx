import { CheckCircle2, AlertTriangle, XCircle } from 'lucide-react';

import type { StatusTone } from './statusTone';

const ICONS = { good: CheckCircle2, warning: AlertTriangle, critical: XCircle } as const;

/** State is carried by the icon shape and the label; the status color only reinforces it. */
export default function StatusPill({ tone, label, title }: { tone: StatusTone; label: string; title?: string }) {
  const Icon = ICONS[tone];
  return (
    <span className={`status-pill ${tone}`} title={title}>
      <Icon size={13} aria-hidden="true" />
      {label}
    </span>
  );
}
