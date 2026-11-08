import type { ReactNode } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { cn } from '@/lib/utils';

interface EventSectionCardProps {
  /** Lets errors scroll to the section. */
  id?: string;
  title: string;
  /** Small text on the right of the header. */
  aside?: ReactNode;
  contentClassName?: string;
  children: ReactNode;
}

/** One section of the edit event form; every section shares this header. */
export function EventSectionCard({
  id,
  title,
  aside,
  contentClassName,
  children,
}: EventSectionCardProps) {
  return (
    <Card
      id={id}
      className="gap-0 overflow-hidden rounded-xl border border-border-strong bg-card py-0 shadow-none"
    >
      <CardHeader className="border-b border-primary/15 bg-accent px-6 py-4! md:px-8">
        <CardTitle className="flex items-baseline gap-2 text-xl font-bold text-foreground">
          <span>{title}</span>
          {aside ? (
            <span className="ml-auto text-[11px] font-semibold text-muted-foreground">{aside}</span>
          ) : null}
        </CardTitle>
      </CardHeader>
      <CardContent className={cn('space-y-5 px-6 py-6 md:px-8', contentClassName)}>
        {children}
      </CardContent>
    </Card>
  );
}
