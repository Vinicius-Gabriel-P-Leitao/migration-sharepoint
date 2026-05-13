import * as React from 'react';
import { Button } from '@lib/components/ui/button';

export interface ShButtonProps extends React.ComponentProps<typeof Button> {
  asChild?: boolean;
}

export const ShButton = React.forwardRef<HTMLButtonElement, ShButtonProps>(
  ({ children, ...props }, ref) => {
    return (
      <Button ref={ref} {...props}>
        {children}
      </Button>
    );
  }
);

ShButton.displayName = 'ShButton';
