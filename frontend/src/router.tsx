import { createBrowserRouter, Navigate } from 'react-router';

import AdminEventsPage from './admin/AdminEventsPage';
import AdminLayout from './admin/AdminLayout';
import CategoriesPage from './admin/CategoriesPage';
import VenuesPage from './admin/VenuesPage';
import { RequireRole } from './auth/RequireRole';
import Layout from './components/Layout';
import EventManageDetailPage from './events/EventManageDetailPage';
import EventDetailPage from './pages/EventDetailPage';
import EventsPage from './pages/EventsPage';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import EventFormPage from './organizer/EventFormPage';
import OrganizerEventsPage from './organizer/OrganizerEventsPage';
import OrganizerLayout from './organizer/OrganizerLayout';
import OrganizationDetailPage from './organizer/organizations/OrganizationDetailPage';
import OrganizationsPage from './organizer/organizations/OrganizationsPage';

// Trang công khai thêm vào children của Layout; trang quản trị thêm vào children của AdminLayout
// kèm handle.crumb để có breadcrumb.
export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'events', element: <EventsPage /> },
      { path: 'events/:id', element: <EventDetailPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  {
    path: '/admin',
    element: <RequireRole role="ADMIN" />,
    children: [
      {
        element: <AdminLayout />,
        handle: { crumb: 'Quản trị' },
        children: [
          { index: true, element: <Navigate to="events" replace /> },
          {
            path: 'events',
            handle: { crumb: 'Sự kiện' },
            children: [
              { index: true, element: <AdminEventsPage key="all" mode="all" /> },
              { path: 'pending', element: <AdminEventsPage key="pending" mode="pending" />, handle: { crumb: 'Chờ duyệt' } },
              { path: ':id', element: <EventManageDetailPage key="admin" area="admin" />, handle: { crumb: 'Chi tiết' } },
            ],
          },
          { path: 'categories', element: <CategoriesPage />, handle: { crumb: 'Danh mục sự kiện' } },
          { path: 'venues', element: <VenuesPage />, handle: { crumb: 'Địa điểm' } },
        ],
      },
    ],
  },
  {
    path: '/organizer',
    element: <RequireRole role="USER" exclude="ADMIN" />,
    children: [
      {
        element: <OrganizerLayout />,
        handle: { crumb: 'Ban tổ chức' },
        children: [
          { index: true, element: <Navigate to="events" replace /> },
          {
            path: 'events',
            handle: { crumb: 'Sự kiện của tôi' },
            children: [
              { index: true, element: <OrganizerEventsPage /> },
              { path: ':id', element: <EventManageDetailPage key="organizer" area="organizer" />, handle: { crumb: 'Chi tiết' } },
              { path: ':id/edit', element: <EventFormPage />, handle: { crumb: 'Sửa sự kiện' } },
            ],
          },
          {
            path: 'organizations',
            handle: { crumb: 'Tổ chức' },
            children: [
              { index: true, element: <OrganizationsPage /> },
              { path: ':id', element: <OrganizationDetailPage />, handle: { crumb: 'Chi tiết tổ chức' } },
            ],
          },
        ],
      },
    ],
  },
]);
