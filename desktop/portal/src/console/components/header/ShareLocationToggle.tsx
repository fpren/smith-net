import { useShareLocation } from '../../hooks/useShareLocation';
import { useShareLocationStore } from '../../stores/shareLocationStore';

export function ShareLocationToggle() {
  const { start, stop } = useShareLocation();
  const isSharing = useShareLocationStore((s) => s.isSharing);
  const isTransitioning = useShareLocationStore((s) => s.isTransitioning);
  const error = useShareLocationStore((s) => s.error);

  const onClick = () => {
    if (isTransitioning) return;
    isSharing ? stop() : start();
  };

  const label = isSharing ? 'Share Location: ON' : 'Share Location: OFF';
  const colorClass = isSharing
    ? 'border-sn-accent text-sn-accent'
    : 'border-sn-line text-sn-ink-muted hover:text-sn-accent hover:border-sn-accent';

  return (
    <div className="flex items-center gap-2">
      <button
        type="button"
        onClick={onClick}
        disabled={isTransitioning}
        className={`rounded-full font-mono text-xs px-3 py-1 border ${colorClass} disabled:opacity-50`}
      >
        {label}
      </button>
      {error && <span className="text-xs text-sn-attention">{error}</span>}
    </div>
  );
}
