import * as React from 'react';
import {
  Drawer,
  DrawerPortal,
  DrawerOverlay,
  DrawerTrigger,
  DrawerClose,
  DrawerContent,
  DrawerHeader,
  DrawerFooter,
  DrawerTitle,
  DrawerDescription,
} from '@lib/components/ui/drawer';
import { cn } from '@lib/utils/cn.util';

export const ShDrawer = Drawer;
export const ShDrawerPortal = DrawerPortal;
export const ShDrawerOverlay = DrawerOverlay;
export const ShDrawerTrigger = DrawerTrigger;
export const ShDrawerClose = DrawerClose;

export const ShDrawerContent = React.forwardRef<
  React.ElementRef<typeof DrawerContent>,
  React.ComponentPropsWithoutRef<typeof DrawerContent>
>(({ className, children, ...props }, ref) => (
  <DrawerContent ref={ref} className={cn(className)} {...props}>
    {children}
  </DrawerContent>
));
ShDrawerContent.displayName = 'ShDrawerContent';

export const ShDrawerHeader = DrawerHeader;
export const ShDrawerFooter = DrawerFooter;
export const ShDrawerTitle = DrawerTitle;
export const ShDrawerDescription = DrawerDescription;

export const ShDrawerBody = ({ className, ...props }: React.ComponentProps<'div'>) => (
  <div className={cn('flex-1 overflow-y-auto p-6', className)} {...props} />
);
ShDrawerBody.displayName = 'ShDrawerBody';
