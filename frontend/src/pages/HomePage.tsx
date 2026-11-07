import { useEffect, useState } from 'react'

import { errorMessage } from '../api/client'
import { searchEvents, type EventItem } from '../api/events'

/** Danh sách sự kiện PUBLISHED, đồng thời kiểm tra SPA gọi được Gateway. */
export default function HomePage() {
  const [events, setEvents] = useState<EventItem[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    searchEvents()
      .then((page) => setEvents(page.content))
      .catch((err: unknown) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [])

  return (
    <section>
      <h1 className="mb-4 text-2xl font-bold tracking-tight md:text-[28px]">Sự kiện sắp diễn ra</h1>
      {loading && <p className="text-muted-foreground">Đang tải…</p>}
      {error && <p className="text-destructive">{error}</p>}
      {!loading && !error && events.length === 0 && <p className="text-muted-foreground">Chưa có sự kiện nào.</p>}
      <ul className="grid gap-4 sm:grid-cols-2 md:gap-6 lg:grid-cols-3">
        {events.map((event) => (
          <li key={event.id} className="rounded-xl border bg-card p-4 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
            <p className="text-xs font-semibold text-accent-foreground">{event.category.name}</p>
            <h2 className="line-clamp-2 font-semibold">{event.title}</h2>
            <p className="mt-1 text-[13px] text-muted-foreground">
              {new Date(event.startTime).toLocaleString('vi-VN')} · {event.venue.city}
            </p>
          </li>
        ))}
      </ul>
    </section>
  )
}
