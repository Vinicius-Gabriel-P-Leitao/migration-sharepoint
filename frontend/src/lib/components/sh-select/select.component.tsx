import * as React from 'react';
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectSeparator,
  SelectTrigger,
  SelectValue,
} from '@lib/components/ui/select';
import { cn } from '@lib/utils/cn.util';

interface ShSelectProps {
  value?: string;
  onValueChange?: (value: string) => void;
  defaultValue?: string;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  children: React.ReactNode;
  size?: 'sm' | 'default';
  position?: 'item-aligned' | 'popper';
}

export const ShSelect = ({
  value,
  onValueChange,
  defaultValue,
  placeholder = 'Selecione...',
  disabled,
  className,
  children,
  size,
  position = 'item-aligned',
}: ShSelectProps) => {
  return (
    <Select value={value} onValueChange={onValueChange} defaultValue={defaultValue}>
      <SelectTrigger className={cn('w-full', className)} disabled={disabled} size={size}>
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent position={position}>{children}</SelectContent>
    </Select>
  );
};

export {
  SelectItem as ShSelectItem,
  SelectGroup as ShSelectGroup,
  SelectLabel as ShSelectLabel,
  SelectSeparator as ShSelectSeparator,
};
