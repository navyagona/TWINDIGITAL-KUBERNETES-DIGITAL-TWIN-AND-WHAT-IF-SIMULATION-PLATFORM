export type StatusTone = 'good' | 'warning' | 'critical';

/** PASS/WARN/FAIL from simulation checks */
export function checkTone(status: string | undefined): StatusTone {
  if (status === 'FAIL') return 'critical';
  if (status === 'WARN') return 'warning';
  return 'good';
}

/** Spring actuator status: UP is good, DOWN is critical, anything else (OUT_OF_SERVICE, UNKNOWN) is a warning */
export function healthTone(status: string | undefined): StatusTone {
  if (status === 'UP') return 'good';
  if (status === 'DOWN') return 'critical';
  return 'warning';
}
