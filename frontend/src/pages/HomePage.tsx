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
      <h1 className="mb-4 text-2xl font-bold">Sự kiện sắp diễn ra</h1>
      {loading && <p className="text-slate-500">Đang tải…</p>}
      {error && <p className="text-red-600">{error}</p>}
      {!loading && !error && events.length === 0 && <p className="text-slate-500">Chưa có sự kiện nào.</p>}
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {events.map((event) => (
          <li key={event.id} className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-sm text-indigo-600">{event.category.name}</p>
            <h2 className="font-semibold">{event.title}</h2>
            <p className="text-sm text-slate-600">
              {new Date(event.startTime).toLocaleString('vi-VN')} · {event.venue.city}
            </p>
          </li>
        ))}
      </ul>
    </section>
  )
}
