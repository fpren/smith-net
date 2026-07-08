// desktop/portal/src/console/components/comm/DialRail.tsx
// Right zone of the comm softphone: dial an id + your own id card.

import { DialField } from './DialField';
import { MyIdCard } from './MyIdCard';

export function DialRail() {
  return (
    <aside className="comm-surface hidden lg:flex lg:flex-col lg:w-80 lg:flex-shrink-0 border-l border-sn-line bg-sn-bg-panel p-4">
      <DialField />
      <div className="mt-auto" />
      <MyIdCard />
    </aside>
  );
}
