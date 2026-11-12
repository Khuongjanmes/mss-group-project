import { Fragment, Suspense } from 'react';
import { Link, useMatches } from 'react-router';
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb';
import type { RouteHandle } from './types';

/** Builds the trail from `handle.crumb` of the matched routes. */
export function AppBreadcrumb() {
  const crumbs = useMatches().flatMap((match) => {
    const Crumb = (match.handle as RouteHandle | undefined)?.crumb;
    if (!Crumb) return [];
    const label =
      typeof Crumb === 'string' ? (
        Crumb
      ) : (
        <Suspense fallback="…">
          <Crumb params={match.params} />
        </Suspense>
      );
    return [{ label, to: match.pathname }];
  });

  if (crumbs.length === 0) return null;

  return (
    <Breadcrumb>
      <BreadcrumbList>
        {crumbs.map((crumb, index) => {
          const isLast = index === crumbs.length - 1;
          return (
            <Fragment key={crumb.to}>
              {index > 0 ? <BreadcrumbSeparator /> : null}
              <BreadcrumbItem>
                {isLast ? (
                  <BreadcrumbPage>{crumb.label}</BreadcrumbPage>
                ) : (
                  <BreadcrumbLink asChild>
                    <Link to={crumb.to}>{crumb.label}</Link>
                  </BreadcrumbLink>
                )}
              </BreadcrumbItem>
            </Fragment>
          );
        })}
      </BreadcrumbList>
    </Breadcrumb>
  );
}
