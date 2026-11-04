import { Children, cloneElement, forwardRef, isValidElement, type HTMLAttributes, type ReactElement } from 'react';
import { cn } from '@/lib/cn';

/** Minimal Radix-style Slot: merges props onto the single child (for `asChild`). */
export const Slot = forwardRef<HTMLElement, HTMLAttributes<HTMLElement> & { children?: React.ReactNode }>(function Slot({ children, className, ...rest }, ref) {
  const child = Children.only(children);
  if (!isValidElement(child)) return null;
  const el = child as ReactElement<Record<string, unknown>>;
  return cloneElement(el, { ...rest, ...el.props, ref, className: cn(className, el.props.className as string) });
});
