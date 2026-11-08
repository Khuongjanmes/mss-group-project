import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';
import { Slot } from 'radix-ui';

const badgeVariants = cva(
  'inline-flex w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-full border border-transparent px-2 py-0.5 text-xs font-medium whitespace-nowrap transition-[color,box-shadow] focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring aria-invalid:border-destructive aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 [&>svg]:pointer-events-none [&>svg]:size-3',
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground [a&]:hover:bg-primary/90',
        secondary: 'bg-secondary text-secondary-foreground [a&]:hover:bg-secondary/90',
        destructive:
          'bg-destructive text-white focus-visible:ring-destructive/20 dark:bg-destructive/60 dark:focus-visible:ring-destructive/40 [a&]:hover:bg-destructive/90',
        outline:
          'border-border text-foreground [a&]:hover:bg-accent [a&]:hover:text-accent-foreground',
        ghost: '[a&]:hover:bg-accent [a&]:hover:text-accent-foreground',
        link: 'text-primary underline-offset-4 [a&]:hover:underline',
        // Semantic variants — pick by meaning, see DESIGN.md §5 "Badge".
        neutral: 'border-border bg-muted text-muted-foreground',
        soft: 'bg-accent text-accent-foreground',
        success: 'bg-success/12 text-success',
        warning: 'bg-warning text-warning-foreground',
        info: 'bg-info/12 text-info',
        live: 'bg-live text-live-foreground',
        inverse: 'bg-foreground text-background',
        // Figma UI kit badges (Rounded, outline only): 6px radius, coloured border and text.
        blue: 'rounded-sm border-badge-blue-border text-badge-blue-foreground',
        purple: 'rounded-sm border-badge-purple-border text-badge-purple-foreground',
        red: 'rounded-sm border-badge-red-border text-badge-red-foreground',
        green: 'rounded-sm border-badge-green-border text-badge-green-foreground',
        yellow: 'rounded-sm border-badge-yellow-border text-badge-yellow-foreground',
        grey: 'rounded-sm border-badge-grey-border text-badge-grey-foreground',
      },
      size: {
        default: '',
        label: 'text-xs font-semibold',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
);

function Badge({
  className,
  variant = 'default',
  size = 'default',
  asChild = false,
  ...props
}: React.ComponentProps<'span'> & VariantProps<typeof badgeVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot.Root : 'span';

  return (
    <Comp
      data-slot="badge"
      data-variant={variant}
      className={cn(badgeVariants({ variant, size }), className)}
      {...props}
    />
  );
}

type BadgeVariant = NonNullable<VariantProps<typeof badgeVariants>['variant']>;

export { Badge, badgeVariants, type BadgeVariant };
