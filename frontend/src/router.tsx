import { createBrowserRouter, Navigate } from 'react-router';

import AdminEventsPage from './admin/AdminEventsPage';
import AdminLayout from './admin/AdminLayout';
import CategoriesPage from './admin/CategoriesPage';
import PaymentsPage from './admin/PaymentsPage';
import VenuesPage from './admin/VenuesPage';
import { RequireRole } from './auth/RequireRole';
import Layout from './components/Layout';
import EventManageDetailPage from './events/EventManageDetailPage';
import EventDetailPage from './pages/EventDetailPage';
import EventsPage from './pages/EventsPage';
import LoginPage from './pages/LoginPage';
import MockPaymentPage from './pages/MockPaymentPage';
import MyOrdersPage from './pages/MyOrdersPage';
import MyTicketsPage from './pages/MyTicketsPage';
import NotificationsPage from './pages/NotificationsPage';
import NotFoundPage from './pages/NotFoundPage';
import PaymentResultPage from './pages/PaymentResultPage';
import CheckInEventsPage from './organizer/check-in/CheckInEventsPage';
import CheckInScanPage from './organizer/check-in/CheckInScanPage';
import EventFormPage from './organizer/EventFormPage';
import OrganizerEventsPage from './organizer/OrganizerEventsPage';
import OrganizerLayout from './organizer/OrganizerLayout';
import OrganizationDetailPage from './organizer/organizations/OrganizationDetailPage';
import OrganizationsPage from './organizer/organizations/OrganizationsPage';

// Trang công khai thêm vào children của Layout; trang quản trị thêm vào children của AdminLayout
// kèm handle.crumb để có breadcrumb.
export const router = createBrowserRouter([
  // Đăng nhập toàn màn hình, không có header chung (theo openevent).
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <Layout />,
    children: [
      // Không có trang chủ riêng: vào thẳng danh sách sự kiện (như openevent).
      { index: true, element: <Navigate to="/events" replace /> },
      { path: 'events', element: <EventsPage /> },
      { path: 'events/:id', element: <EventDetailPage /> },
      {
        element: <RequireRole role="USER" />,
        children: [
          { path: 'orders', element: <MyOrdersPage /> },
          { path: 'tickets', element: <MyTicketsPage /> },
          { path: 'notifications', element: <NotificationsPage /> },
          { path: 'payments/result', element: <PaymentResultPage /> },
          { path: 'payments/:id/mock', element: <MockPaymentPage /> },
        ],
      },
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
          { path: 'payments', element: <PaymentsPage />, handle: { crumb: 'Giao dịch' } },
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
          {
            path: 'check-in',
            handle: { crumb: 'Check-in' },
            children: [
              { index: true, element: <CheckInEventsPage /> },
              { path: ':eventId', element: <CheckInScanPage />, handle: { crumb: 'Soát vé' } },
            ],
          },
        ],
      },
    ],
  },
]);
