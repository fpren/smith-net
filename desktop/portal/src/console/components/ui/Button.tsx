import { ButtonHTMLAttributes, forwardRef } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

type Variant = 'primary' | 'secondary' | 'danger';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
}

const VARIANT_CLASSES: Record<Variant, string> = {
  primary: 'bg-sn-accent text-sn-ink-on-accent hover:opacity-90',
  secondary: 'bg-sn-bg-panel text-sn-ink border border-sn-line hover:bg-sn-bg-base',
  danger: 'bg-sn-status-error text-sn-ink-on-accent hover:opacity-90',
};

export const Button = forwardRef<HTMLButtonElement, Props>(function Button(
  { variant = 'primary', className, children, ...rest },
  ref
) {
  return (
    <button
      ref={ref}
      className={twMerge(
        clsx(
          'rounded-full px-4 py-2 font-mono text-sm transition-opacity disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline focus-visible:outline-2 focus-visible:outline-sn-accent',
          VARIANT_CLASSES[variant],
          className
        )
      )}
      {...rest}
    >
      {children}
    </button>
  );
});
