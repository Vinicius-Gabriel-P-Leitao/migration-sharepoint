import * as React from 'react';
import {
  Dialog,
  DialogPortal,
  DialogOverlay,
  DialogClose,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogFooter,
  DialogTitle,
  DialogDescription,
} from '@/lib/components/ui/dialog';

const SIZE_MAP = {
  sm:   { maxWidth: '24rem' },
  md:   { maxWidth: '28rem' },
  lg:   { maxWidth: '32rem' },
  xl:   { maxWidth: '36rem' },
  '2xl': { maxWidth: '42rem' },
  '3xl': { maxWidth: '48rem' },
  '4xl': { maxWidth: '56rem' },
  '5xl': { maxWidth: '64rem' },
  full: { maxWidth: 'calc(100vw - 2rem)' },
} as const;

type DialogSize = keyof typeof SIZE_MAP;

interface ShDialogContentProps
  extends React.ComponentPropsWithoutRef<typeof DialogContent> {
  size?: DialogSize;
  maxWidth?: React.CSSProperties['maxWidth'];
  maxHeight?: React.CSSProperties['maxHeight'];
}

export const ShDialogContent = ({
  size = 'md',
  maxWidth,
  maxHeight,
  style,
  ...props
}: ShDialogContentProps) => {
  const sizeStyle = SIZE_MAP[size];

  return (
    <DialogContent
      style={{
        maxWidth: maxWidth ?? sizeStyle.maxWidth,
        maxHeight: maxHeight ?? undefined,
        width: '100%',
        ...style,
      }}
      {...props}
    />
  );
};

export const ShDialog = Dialog;
export const ShDialogPortal = DialogPortal;
export const ShDialogOverlay = DialogOverlay;
export const ShDialogClose = DialogClose;
export const ShDialogTrigger = DialogTrigger;
export const ShDialogHeader = DialogHeader;
export const ShDialogFooter = DialogFooter;
export const ShDialogTitle = DialogTitle;
export const ShDialogDescription = DialogDescription;
