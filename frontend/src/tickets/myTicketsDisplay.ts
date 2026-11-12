import type { EventItem } from '@/api/events';
import type { Ticket, TicketStatus } from '@/api/tickets';
import type { BadgeVariant } from '@/components/ui/badge';
import { formatDate } from '@/lib/datetime';

/** Vé của một sự kiện gom lại thành một thẻ, như một lượt đăng ký ở "Sự kiện của tôi" của openevent. */
export type EventTickets = {
  eventId: string;
  /** `undefined` khi không tải được sự kiện (bị ẩn hoặc Event lỗi): thẻ chỉ còn giờ và loại vé. */
  event?: EventItem;
  startTime: string;
  endTime: string;
  tickets: Ticket[];
};

export type Timeframe = 'all' | 'upcoming' | 'ongoing' | 'ended';
export type Phase = 'upcoming' | 'ongoing' | 'ended' | 'cancelled';

export const PHASE_LABELS: Record<Phase, string> = {
  upcoming: 'Sắp diễn ra',
  ongoing: 'Đang diễn ra',
  ended: 'Đã kết thúc',
  cancelled: 'Đã huỷ',
};

/** Sắp diễn ra không cần badge: đã có đếm ngược. */
export const PHASE_BADGE: Record<Phase, BadgeVariant | null> = {
  upcoming: null,
  ongoing: 'live',
  ended: 'info',
  cancelled: 'destructive',
};

/** Gom vé theo sự kiện, sự kiện sắp diễn ra trước, đã kết thúc sau cùng (mới kết thúc trước). */
export function groupByEvent(tickets: Ticket[], events: Map<string, EventItem>, now = new Date()): EventTickets[] {
  const groups = new Map<string, EventTickets>();
  for (const ticket of tickets) {
    if (!ticket.eventId || !ticket.eventStartTime || !ticket.eventEndTime) continue;
    const group = groups.get(ticket.eventId) ?? {
      eventId: ticket.eventId,
      event: events.get(ticket.eventId),
      startTime: events.get(ticket.eventId)?.startTime ?? ticket.eventStartTime,
      endTime: events.get(ticket.eventId)?.endTime ?? ticket.eventEndTime,
      tickets: [],
    };
    group.tickets.push(ticket);
    groups.set(ticket.eventId, group);
  }
  const ended = (g: EventTickets) => phaseOf(g, now) === 'ended' || phaseOf(g, now) === 'cancelled';
  return [...groups.values()].sort((a, b) =>
    ended(a) !== ended(b) ? (ended(a) ? 1 : -1)
      : ended(a) ? b.endTime.localeCompare(a.endTime) : a.startTime.localeCompare(b.startTime));
}

export function phaseOf(group: EventTickets, now = new Date()): Phase {
  if (group.event?.status === 'CANCELLED' || group.tickets.every((t) => t.status === 'CANCELLED')) return 'cancelled';
  if (group.event?.status === 'COMPLETED' || new Date(group.endTime) <= now) return 'ended';
  if (new Date(group.startTime) <= now) return 'ongoing';
  return 'upcoming';
}

export function inTimeframe(group: EventTickets, timeframe: Timeframe, now = new Date()): boolean {
  const phase = phaseOf(group, now);
  if (timeframe === 'all') return true;
  if (timeframe === 'ended') return phase === 'ended' || phase === 'cancelled';
  return phase === timeframe;
}

/** Ngày lịch Việt Nam của `value`, để đếm số ngày chẵn. */
function vietnamDay(value: string | Date): number {
  const [day, month, year] = formatDate(typeof value === 'string' ? value : value.toISOString()).split('/').map(Number);
  return Date.UTC(year, month - 1, day);
}

/** Số ngày lịch tới khi bắt đầu (0 = hôm nay); `null` khi đã bắt đầu, kết thúc hoặc bị huỷ. */
export function daysUntilStart(group: EventTickets, now = new Date()): number | null {
  if (phaseOf(group, now) !== 'upcoming') return null;
  return Math.max(0, Math.round((vietnamDay(group.startTime) - vietnamDay(now)) / 86_400_000));
}

export function countdownLabel(days: number): string {
  if (days === 0) return 'Hôm nay';
  if (days === 1) return 'Ngày mai';
  return `Còn ${days} ngày`;
}

export function countdownVariant(days: number): BadgeVariant {
  if (days === 0) return 'live';
  if (days <= 3) return 'warning';
  return 'soft';
}

/** `VIP ×2 · Thường ×1`. */
export function ticketTypesSummary(tickets: Ticket[]): string {
  const counts = new Map<string, number>();
  for (const t of tickets) counts.set(t.ticketTypeName ?? 'Vé', (counts.get(t.ticketTypeName ?? 'Vé') ?? 0) + 1);
  return [...counts].map(([name, n]) => `${name} ×${n}`).join(' · ');
}

/** Badge số vé theo trạng thái: `2 vé · 1 đã check-in`. */
export function ticketCountText(tickets: Ticket[]): string {
  const by = (status: TicketStatus) => tickets.filter((t) => t.status === status).length;
  const parts = [`${tickets.length} vé`];
  if (by('CHECKED_IN')) parts.push(`${by('CHECKED_IN')} đã check-in`);
  if (by('CANCELLED')) parts.push(`${by('CANCELLED')} đã huỷ`);
  return parts.join(' · ');
}

export function emptyStateText(filtered: boolean, timeframe: Timeframe): { title: string; description: string } {
  if (filtered) return { title: 'Không tìm thấy vé phù hợp', description: 'Thử từ khoá hoặc bộ lọc khác.' };
  switch (timeframe) {
    case 'upcoming':
      return { title: 'Bạn không có sự kiện sắp diễn ra', description: 'Tìm sự kiện phù hợp và mua vé để tham gia.' };
    case 'ongoing':
      return { title: 'Không có sự kiện nào đang diễn ra', description: 'Vé của sự kiện đang diễn ra sẽ hiện ở đây.' };
    case 'ended':
      return { title: 'Chưa có sự kiện nào đã diễn ra', description: 'Vé của các sự kiện đã kết thúc sẽ hiện ở đây.' };
    default:
      return { title: 'Bạn chưa có vé nào', description: 'Vé xuất hiện ở đây sau khi thanh toán thành công.' };
  }
}
