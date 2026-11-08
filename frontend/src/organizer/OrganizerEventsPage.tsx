import { Plus } from 'lucide-react';
import { useState } from 'react';

import { type EventStatus, searchMyEvents } from '@/api/events';
import { Button } from '@/components/ui/button';
import { EventListPage, type EventQuery } from '@/events/EventListPage';
import { CreateEventModal } from './CreateEventModal';

const STATUSES: EventStatus[] = ['DRAFT', 'PENDING_APPROVAL', 'REJECTED', 'PUBLISHED', 'CANCELLED', 'COMPLETED'];

// Ngoài component để EventListPage không tải lại mỗi lần render.
const load = ({ status, page, size }: EventQuery) => searchMyEvents({ status, page, size });

/** Sự kiện mình làm HOST: tạo nháp, sửa, xóa, gửi duyệt; bị từ chối thì sửa và gửi lại (BR-05). */
export default function OrganizerEventsPage() {
  const [creating, setCreating] = useState(false);

  return (
    <>
      <EventListPage
        title="Sự kiện của tôi"
        description="Tạo nháp, sửa, rồi gửi Admin duyệt. Bị từ chối thì sửa theo lý do và gửi lại."
        area="organizer"
        load={load}
        statuses={STATUSES}
        emptyTitle="Bạn chưa tạo sự kiện nào"
        emptyDescription='Bấm "Tạo sự kiện" để bắt đầu.'
        headerActions={
          <Button onClick={() => setCreating(true)} className="h-9 rounded-lg px-3 font-semibold">
            <Plus />
            Tạo sự kiện
          </Button>
        }
      />
      <CreateEventModal open={creating} onOpenChange={setCreating} />
    </>
  );
}
