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
    ? 'border-console-accent text-console-accent'
    : 'border-console-border text-console-text-muted hover:text-console-accent hover:border-console-accent';

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
      {error && <span className="text-xs text-console-warn">{error}</span>}
    </div>
  );
}
