import * as S from '@radix-ui/react-switch';
import { cn } from '@/lib/cn';

export function Switch({ checked, onCheckedChange, label, disabled, className, id }: { checked: boolean; onCheckedChange: (v: boolean) => void; label: string; disabled?: boolean; className?: string; id?: string }) {
  return (
    <S.Root
      id={id}
      checked={checked}
      onCheckedChange={onCheckedChange}
      disabled={disabled}
      aria-label={label}
      className={cn('relative inline-flex h-6 w-11 shrink-0 items-center rounded-full border border-transparent transition-colors duration-150 data-[state=checked]:bg-primary data-[state=unchecked]:bg-border-strong disabled:opacity-50', className)}
    >
      <S.Thumb className="block size-5 translate-x-0.5 rounded-full bg-white shadow-e1 transition-transform duration-150 data-[state=checked]:translate-x-[21px]" />
    </S.Root>
  );
}
