import { ArrowRight, Search } from 'lucide-react';
import { type FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { errorMessage } from '@/api/client';
import { type Category, type EventItem, listCategories, searchEvents } from '@/api/events';
import { EmptyState, ErrorState } from '@/components/common/feedback';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { CategoryChips } from '@/events/CategoryChips';
import { EventCardGrid, EventCardGridSkeleton } from '@/events/EventCard';

const UPCOMING_COUNT = 6;

/** Trang chủ theo HomePage của openevent SBA301: ô tìm kiếm, danh mục, sự kiện sắp diễn ra. */
export default function HomePage() {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const [categories, setCategories] = useState<Category[]>([]);
  const [events, setEvents] = useState<EventItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    listCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    let ignore = false;
    setError(null);
    // API xếp sự kiện PUBLISHED theo giờ bắt đầu tăng dần, nên trang đầu là các sự kiện gần nhất.
    searchEvents({ size: UPCOMING_COUNT })
      .then((page) => !ignore && setEvents(page.content))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const value = keyword.trim();
    navigate(value ? `/events?${new URLSearchParams({ keyword: value })}` : '/events');
  };

  return (
    <div className="space-y-10">
      <section className="rounded-2xl bg-linear-to-br from-accent to-surface-alt px-5 py-10 text-center sm:px-10 md:py-14">
        <h1 className="mx-auto max-w-2xl text-3xl font-bold tracking-tight text-balance md:text-4xl">
          Tìm và tham gia sự kiện bạn quan tâm
        </h1>
        <p className="mx-auto mt-3 max-w-xl text-muted-foreground">
          Hội thảo, workshop, âm nhạc, thể thao… do các câu lạc bộ, trường học và doanh nghiệp tổ chức.
        </p>
        <form onSubmit={submit} className="mx-auto mt-6 flex max-w-xl gap-2" role="search">
          <div className="relative flex-1">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              type="search"
              aria-label="Tìm sự kiện"
              placeholder="Tìm theo tên sự kiện..."
              maxLength={100}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              className="h-11 rounded-lg bg-card pl-9"
            />
          </div>
          <Button type="submit" className="h-11 rounded-lg px-5">
            Tìm kiếm
          </Button>
        </form>
      </section>

      {categories.length > 0 && (
        <section aria-labelledby="home-categories" className="space-y-3">
          <h2 id="home-categories" className="text-lg font-bold tracking-tight">
            Danh mục
          </h2>
          <CategoryChips
            categories={categories}
            value=""
            onChange={(slug) => navigate(slug ? `/events?${new URLSearchParams({ category: slug })}` : '/events')}
          />
        </section>
      )}

      <section aria-labelledby="home-upcoming" className="space-y-4">
        <div className="flex items-end justify-between gap-3">
          <h2 id="home-upcoming" className="text-xl font-bold tracking-tight md:text-2xl">
            Sự kiện sắp diễn ra
          </h2>
          <Button asChild variant="link" className="px-0">
            <Link to="/events">
              Xem tất cả
              <ArrowRight className="size-4" />
            </Link>
          </Button>
        </div>
        {error ? (
          <ErrorState description={error} onRetry={() => setReloadKey((key) => key + 1)} />
        ) : events === null ? (
          <EventCardGridSkeleton count={UPCOMING_COUNT} />
        ) : events.length === 0 ? (
          <EmptyState title="Chưa có sự kiện nào được công bố" description="Quay lại sau nhé." />
        ) : (
          <EventCardGrid events={events} />
        )}
      </section>
    </div>
  );
}
