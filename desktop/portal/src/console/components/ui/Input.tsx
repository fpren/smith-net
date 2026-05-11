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
      <span className="text-console-text-muted text-xs uppercase tracking-wide">{label}</span>
      <input
        ref={ref}
        id={inputId}
        className={twMerge(
          clsx(
            'bg-console-bg border border-console-border px-3 py-2 text-console-text',
            'focus:outline-none focus:border-console-accent',
            error && 'border-console-danger',
            className
          )
        )}
        {...rest}
      />
      {error && <span className="text-console-danger text-xs">{error}</span>}
    </label>
  );
});
