import { Children, cloneElement, forwardRef, isValidElement, type ButtonHTMLAttributes, type ReactElement, type ReactNode } from 'react';
import { Slot } from './Slot';
import { cn } from '@/lib/cn';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'soft';
type Size = 'sm' | 'md' | 'lg';

const base =
  'inline-flex items-center justify-center gap-2 font-semibold select-none whitespace-nowrap rounded-control transition-[background-color,box-shadow,transform,color] duration-150 ease-ss active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2';
const variants: Record<Variant, string> = {
  primary: 'bg-primary text-on-primary hover:bg-primary-strong shadow-e1',
  secondary: 'bg-surface text-text border border-border hover:border-border-strong hover:bg-surface-2',
  ghost: 'text-text hover:bg-surface-2',
  soft: 'bg-primary-soft text-primary-fg hover:brightness-95',
  danger: 'bg-danger text-white hover:brightness-95',
};
const sizes: Record<Size, string> = {
  sm: 'h-9 px-3 text-sm min-w-9',
  md: 'h-11 px-4 text-[15px] min-w-11',
  lg: 'h-12 px-6 text-base min-w-12',
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  asChild?: boolean;
  icon?: ReactNode;
  block?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading, asChild, icon, block, className, children, disabled, ...rest },
  ref,
) {
  const cls = cn(base, variants[variant], sizes[size], block && 'w-full', className);
  const spinner = loading ? <span className="size-4 animate-spin rounded-full border-2 border-current border-r-transparent" aria-hidden /> : icon;
  if (asChild) {
    const child = Children.only(children);
    if (!isValidElement(child)) return null;
    const el = child as ReactElement<{ children?: ReactNode }>;
    return (
      <Slot ref={ref} className={cls} aria-busy={loading || undefined} {...rest}>
        {cloneElement(el, {}, <>{spinner}{el.props.children}</>)}
      </Slot>
    );
  }
  return (
    <button
      ref={ref}
      className={cls}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      type={rest.type ?? 'button'}
      {...rest}
    >
      {spinner}
      {children}
    </button>
  );
});
