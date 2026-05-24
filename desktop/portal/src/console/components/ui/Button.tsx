import { ButtonHTMLAttributes, forwardRef } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

type Variant = 'primary' | 'secondary' | 'danger';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
}

const VARIANT_CLASSES: Record<Variant, string> = {
  primary: 'bg-console-accent text-white hover:opacity-90',
  secondary: 'bg-console-surface text-console-text border border-console-border hover:bg-console-bg',
  danger: 'bg-console-danger text-white hover:opacity-90',
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
          'rounded-full px-4 py-2 font-mono text-sm transition-opacity disabled:opacity-50 disabled:cursor-not-allowed',
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
