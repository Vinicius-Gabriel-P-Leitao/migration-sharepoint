import { useState, useRef } from 'react';
import { ChevronDown, Check } from 'lucide-react';
import { Popover, PopoverTrigger, PopoverContent } from '@/lib/components/ui/popover';
import { ScrollArea } from '@/lib/components/ui/scroll-area';
import { cn } from '@/lib/utils/cn.util';

export interface ShPopoverSelectItem {
  value: string;
  label: string;
  muted?: boolean;
  disabled?: boolean;
}

interface ShPopoverSelectProps {
  value?: string;
  onValueChange?: (value: string) => void;
  items: (ShPopoverSelectItem | 'separator')[];
  placeholder?: string;
  disabled?: boolean;
  size?: 'sm' | 'default';
  className?: string;
}

export const ShPopoverSelect = ({
  value,
  onValueChange,
  items,
  placeholder = 'Selecione...',
  disabled,
  size = 'default',
  className,
}: ShPopoverSelectProps) => {
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);

  const selectedItem = items.find(
    (item): item is ShPopoverSelectItem => item !== 'separator' && item.value === value
  );

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          ref={triggerRef}
          type="button"
          disabled={disabled}
          data-size={size}
          data-state={open ? 'open' : 'closed'}
          className={cn(
            'flex w-full items-center justify-between gap-1.5 rounded-md border border-input bg-transparent pl-2.5 pr-2 py-2 text-sm whitespace-nowrap shadow-xs transition-[color,box-shadow] outline-none',
            'focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50',
            'disabled:cursor-not-allowed disabled:opacity-50',
            'data-[size=default]:h-9 data-[size=sm]:h-8',
            className
          )}
        >
          <span
            className={cn(
              'truncate',
              (!selectedItem || selectedItem.muted) && 'text-muted-foreground'
            )}
          >
            {selectedItem?.label ?? placeholder}
          </span>
          <ChevronDown className="pointer-events-none size-4 shrink-0 text-muted-foreground" />
        </button>
      </PopoverTrigger>
      <PopoverContent
        style={{ width: triggerRef.current?.offsetWidth }}
        className="p-1"
      >
        <ScrollArea className="max-h-[220px]">
        {items.map((item, index) => {
          if (item === 'separator') {
            return <div key={`sep-${index}`} className="-mx-1 my-1 h-px bg-foreground/5" />;
          }
          const isSelected = value === item.value;
          return (
            <button
              key={item.value}
              type="button"
              disabled={item.disabled}
              onClick={() => {
                onValueChange?.(item.value);
                setOpen(false);
              }}
              className={cn(
                'relative flex w-full cursor-default items-center rounded-sm py-1.5 pl-2 pr-8 text-sm outline-none select-none',
                'hover:bg-foreground/10 focus:bg-foreground/10',
                'disabled:pointer-events-none disabled:opacity-50',
                item.muted && 'text-muted-foreground'
              )}
            >
              <span className="truncate">{item.label}</span>
              {isSelected && (
                <span className="pointer-events-none absolute right-2 flex size-4 items-center justify-center">
                  <Check className="size-3.5 pointer-events-none" />
                </span>
              )}
            </button>
          );
        })}
        </ScrollArea>
      </PopoverContent>
    </Popover>
  );
};
