// desktop/portal/src/console/hooks/useToast.ts
import { useToastStore } from '../stores/toastStore';

const DEFAULT_DURATION = 4000;

export function useToast() {
  const push = useToastStore((s) => s.push);
  return {
    info: (message: string, duration: number = DEFAULT_DURATION) =>
      push({ message, tone: 'info', duration }),
    error: (message: string, duration: number = DEFAULT_DURATION) =>
      push({ message, tone: 'error', duration }),
  };
}
