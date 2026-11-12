import type { LucideIcon } from 'lucide-react';
import type { ComponentType, LazyExoticComponent } from 'react';
import type { Params } from 'react-router';

export type NavItem = {
  title: string;
  to: string;
  icon: LucideIcon;
  /** Only active on an exact path match (use for index routes such as `/admin`). */
  end?: boolean;
};


export type NavGroup = {
  label: string;
  items: NavItem[];
};

/** Crumb whose label depends on the URL, e.g. the title of the event in `/admin/events/:eventId`. */
export type DynamicCrumb =
  ComponentType<{ params: Params }> | LazyExoticComponent<ComponentType<{ params: Params }>>;

/** Route `handle` shape read by `AppBreadcrumb`. */
export type RouteHandle = {
  crumb?: string | DynamicCrumb;
};
