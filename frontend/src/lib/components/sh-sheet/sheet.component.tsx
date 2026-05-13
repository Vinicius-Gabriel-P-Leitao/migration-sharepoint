import * as React from 'react';
import {
  Sheet,
  SheetPortal,
  SheetOverlay,
  SheetTrigger,
  SheetClose,
  SheetContent,
  SheetHeader,
  SheetBody,
  SheetFooter,
  SheetTitle,
  SheetDescription,
} from '@lib/components/ui/sheet';

export const ShSheet = Sheet;
export const ShSheetPortal = SheetPortal;
export const ShSheetOverlay = SheetOverlay;
export const ShSheetTrigger = SheetTrigger;
export const ShSheetClose = SheetClose;

export const ShSheetContent = React.forwardRef<
  React.ElementRef<typeof SheetContent>,
  React.ComponentPropsWithoutRef<typeof SheetContent>
>(({ children, ...props }, ref) => (
  <SheetContent ref={ref} aria-describedby={props['aria-describedby'] ?? undefined} {...props}>
    {children}
  </SheetContent>
));
ShSheetContent.displayName = 'ShSheetContent';

export const ShSheetHeader = SheetHeader;
export const ShSheetBody = SheetBody;
export const ShSheetFooter = SheetFooter;
export const ShSheetTitle = SheetTitle;
export const ShSheetDescription = SheetDescription;
