import { useEffect, useState } from 'react';
import { Link } from 'react-router';

import { type EventItem, searchEvents } from '@/api/events';
import { Button } from '@/components/ui/button';
import { EventCardGrid } from './EventCard';

const RELATED_COUNT = 3;

/** Sự kiện PUBLISHED khác cùng danh mục ở cuối trang chi tiết; không có hoặc lỗi thì ẩn. */
export function RelatedEvents({ event }: { event: EventItem }) {
  const [related, setRelated] = useState<EventItem[]>([]);

  useEffect(() => {
    let ignore = false;
    setRelated([]);
    // Lấy dư một để còn đủ sau khi bỏ sự kiện đang xem.
    searchEvents({ category: event.category.slug, size: RELATED_COUNT + 1 })
      .then((page) => {
        if (!ignore) setRelated(page.content.filter((item) => item.id !== event.id).slice(0, RELATED_COUNT));
      })
      .catch(() => !ignore && setRelated([]));
    return () => {
      ignore = true;
    };
  }, [event.id, event.category.slug]);

  if (related.length === 0) return null;
  return (
    <section aria-labelledby="related-events" className="space-y-4 border-t pt-8">
      <div className="flex items-end justify-between gap-3">
        <h2 id="related-events" className="text-xl font-bold tracking-tight">
          Sự kiện khác cùng danh mục
        </h2>
        <Button asChild variant="link" className="px-0">
          <Link to={`/events?${new URLSearchParams({ category: event.category.slug })}`}>Xem thêm</Link>
        </Button>
      </div>
      <EventCardGrid events={related} />
    </section>
  );
}
