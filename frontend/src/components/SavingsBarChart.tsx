import { useEffect, useRef, useState } from 'react';

export interface SavingsBar {
  key: string;
  label: string;        // row label (workload / pod)
  sublabel?: string;    // shown in the tooltip
  value: number;        // USD per month
  detail?: string[];    // extra tooltip lines
}

const ROW_H = 26;       // row pitch; bar is thinner, leaving a gap well over 2px between bars
const BAR_H = 14;
const LABEL_W = 170;
const VALUE_W = 64;
const AXIS_H = 22;
const RADIUS = 4;

function niceStep(max: number): number {
  if (max <= 0) return 1;
  const raw = max / 4;
  const mag = 10 ** Math.floor(Math.log10(raw));
  const norm = raw / mag;
  return (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10) * mag;
}

/** Horizontal bar with a 4px rounded data end; square at the baseline. */
function barPath(x: number, y: number, w: number, h: number): string {
  if (w <= 0) return '';
  const r = Math.min(RADIUS, w, h / 2);
  return `M${x},${y} h${w - r} a${r},${r} 0 0 1 ${r},${r} v${h - 2 * r} a${r},${r} 0 0 1 ${-r},${r} h${-(w - r)} z`;
}

const usd = (v: number) => `$${v.toFixed(2)}`;

/**
 * Single-series horizontal bar chart of estimated monthly savings. One measure, one hue
 * (--series-1), so no legend: the caller's heading names the series. Every bar has a full-row
 * hover target with a tooltip; the right-sizing table beside it is the table view.
 */
export default function SavingsBarChart({ bars, ariaLabel }: { bars: SavingsBar[]; ariaLabel: string }) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(640);
  const [hover, setHover] = useState<number | null>(null);

  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const ro = new ResizeObserver(([entry]) => setWidth(Math.max(320, entry.contentRect.width)));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const sorted = [...bars].sort((a, b) => b.value - a.value);
  if (sorted.length === 0) return null;

  const max = Math.max(...sorted.map((b) => b.value), 0.01);
  const step = niceStep(max);
  const axisMax = Math.ceil(max / step) * step;
  const ticks = Array.from({ length: Math.round(axisMax / step) + 1 }, (_, i) => i * step);

  const plotX = LABEL_W;
  const plotW = Math.max(80, width - LABEL_W - VALUE_W);
  const height = sorted.length * ROW_H + AXIS_H;
  const xOf = (v: number) => plotX + (v / axisMax) * plotW;
  // Label every bar when the list is short; otherwise only the top three
  const labelAll = sorted.length <= 8;

  const hovered = hover != null ? sorted[hover] : null;

  return (
    <div className="bar-chart" ref={wrapRef}>
      <svg width={width} height={height} role="img" aria-label={ariaLabel}>
        {ticks.map((t) => (
          <g key={t}>
            <line className="grid-line" x1={xOf(t)} x2={xOf(t)} y1={0} y2={sorted.length * ROW_H} />
            <text className="axis-label" x={xOf(t)} y={sorted.length * ROW_H + 15} textAnchor="middle">
              ${t % 1 === 0 ? t.toFixed(0) : t.toFixed(2)}
            </text>
          </g>
        ))}

        {sorted.map((b, i) => {
          const y = i * ROW_H + (ROW_H - BAR_H) / 2;
          const w = xOf(b.value) - plotX;
          return (
            <g key={b.key} className="bar-group"
               onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}>
              <text className="axis-label" x={LABEL_W - 10} y={y + BAR_H / 2 + 4} textAnchor="end">
                {b.label.length > 24 ? `${b.label.slice(0, 23)}…` : b.label}
              </text>
              <path className="bar" d={barPath(plotX, y, Math.max(w, 1), BAR_H)} />
              {(labelAll || i < 3) && (
                <text className="value-label" x={plotX + Math.max(w, 1) + 6} y={y + BAR_H / 2 + 4}>
                  {usd(b.value)}
                </text>
              )}
              {/* Hit target: the whole row, larger than the bar */}
              <rect className="bar-hit" x={0} y={i * ROW_H} width={width} height={ROW_H} />
            </g>
          );
        })}
      </svg>

      {hovered && hover != null && (
        <div className="chart-tooltip" role="tooltip"
             style={{ left: Math.min(width - 90, xOf(hovered.value)), top: hover * ROW_H + (ROW_H - BAR_H) / 2 }}>
          <div style={{ fontWeight: 600 }}>{hovered.label}</div>
          {hovered.sublabel && <div className="muted" style={{ fontFamily: 'monospace' }}>{hovered.sublabel}</div>}
          <div>{usd(hovered.value)} / month</div>
          {hovered.detail?.map((d) => <div key={d} className="muted">{d}</div>)}
        </div>
      )}
    </div>
  );
}
