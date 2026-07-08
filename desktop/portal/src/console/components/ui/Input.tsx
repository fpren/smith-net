import { InputHTMLAttributes, forwardRef, useId } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, Props>(function Input(
  { label, error, className, id, ...rest },
  ref
) {
  const autoId = useId();
  const inputId = id ?? autoId;

  return (
    <label htmlFor={inputId} className="flex flex-col gap-1 font-mono text-sm">
      <span className="text-sn-ink-muted text-xs uppercase tracking-wide">{label}</span>
      <input
        ref={ref}
        id={inputId}
        className={twMerge(
          clsx(
            'bg-sn-bg-base border border-sn-line px-3 py-2 text-sn-ink',
            'focus:outline-none focus:border-sn-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-sn-accent',
            error && 'border-sn-status-error',
            className
          )
        )}
        {...rest}
      />
      {error && <span className="text-sn-status-error text-xs">{error}</span>}
    </label>
  );
});
