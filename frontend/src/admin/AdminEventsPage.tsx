import { type EventStatus, searchAdminEvents } from '@/api/events';
import { EventListPage, type EventQuery } from '@/events/EventListPage';

export type EventListMode = 'all' | 'pending';

const STATUSES: EventStatus[] = ['PENDING_APPROVAL', 'PUBLISHED', 'REJECTED', 'CANCELLED', 'COMPLETED', 'DRAFT'];

const loadAll = (query: EventQuery) => searchAdminEvents(query);
const loadPending = ({ keyword, page, size }: EventQuery) =>
  searchAdminEvents({ keyword, status: 'PENDING_APPROVAL', page, size });

/** Admin duyệt và hủy sự kiện, không tạo hay sửa (BR-05, BR-14). */
export default function AdminEventsPage({ mode }: { mode: EventListMode }) {
  return mode === 'pending' ? (
    <EventListPage
      title="Sự kiện chờ duyệt"
      description="Xem nội dung rồi duyệt để công bố, hoặc từ chối kèm lý do."
      area="admin"
      load={loadPending}
      searchable
      emptyTitle="Không có sự kiện nào chờ duyệt"
    />
  ) : (
    <EventListPage
      title="Tất cả sự kiện"
      description="Sự kiện của mọi ban tổ chức, mới tạo trước."
      area="admin"
      load={loadAll}
      statuses={STATUSES}
      searchable
      emptyTitle="Chưa có sự kiện nào"
    />
  );
}
