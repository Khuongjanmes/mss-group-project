import type { Venue } from '@/api/events';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { EventSectionCard } from './EventSectionCard';
import { addHours, type EventFieldErrors, type EventForm } from './eventForm';
import { FIELD_CLASS, FIELD_LABEL_CLASS, SELECT_TRIGGER_CLASS } from './fieldStyles';
import { FieldErrorText } from './FieldErrorText';

type EventTimeLocationCardProps = {
  form: EventForm;
  setField: (field: keyof EventForm, value: string) => void;
  venues: Venue[];
  errors: EventFieldErrors;
  disabled: boolean;
};

export function EventTimeLocationCard({ form, setField, venues, errors, disabled }: EventTimeLocationCardProps) {
  const venue = venues.find((item) => item.id === form.venueId);

  return (
    <EventSectionCard title="Thời gian & Địa điểm">
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <div>
          <label htmlFor="event-start" className={FIELD_LABEL_CLASS}>
            Bắt đầu sự kiện <span className="text-destructive">*</span>
          </label>
          <Input
            id="event-start"
            type="datetime-local"
            disabled={disabled}
            aria-invalid={Boolean(errors.startTime) || undefined}
            value={form.startTime}
            onChange={(e) => {
              setField('startTime', e.target.value);
              // Lần đầu chọn giờ bắt đầu thì gợi ý giờ kết thúc sau 2 tiếng.
              if (e.target.value && !form.endTime) setField('endTime', addHours(e.target.value, 2));
            }}
            className={`${FIELD_CLASS} px-4`}
          />
          <FieldErrorText id="event-start-error" message={errors.startTime} />
        </div>
        <div>
          <label htmlFor="event-end" className={FIELD_LABEL_CLASS}>
            Kết thúc sự kiện <span className="text-destructive">*</span>
          </label>
          <Input
            id="event-end"
            type="datetime-local"
            disabled={disabled}
            aria-invalid={Boolean(errors.endTime) || undefined}
            value={form.endTime}
            min={form.startTime || undefined}
            onChange={(e) => setField('endTime', e.target.value)}
            className={`${FIELD_CLASS} px-4`}
          />
          <FieldErrorText id="event-end-error" message={errors.endTime} />
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <div>
          <label htmlFor="event-venue" className={FIELD_LABEL_CLASS}>
            Địa điểm tổ chức <span className="text-destructive">*</span>
          </label>
          <Select value={form.venueId} disabled={disabled} onValueChange={(value) => setField('venueId', value)}>
            <SelectTrigger
              id="event-venue"
              aria-invalid={Boolean(errors.venueId) || undefined}
              className={SELECT_TRIGGER_CLASS}
            >
              <SelectValue placeholder="Chọn địa điểm" />
            </SelectTrigger>
            <SelectContent>
              {venues.map((item) => (
                <SelectItem key={item.id} value={item.id}>
                  {item.name} · {item.city}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <FieldErrorText id="event-venue-error" message={errors.venueId} />
          {venues.length === 0 && (
            <p className="mt-2 text-xs text-muted-foreground">Chưa có địa điểm nào. Cần tạo địa điểm trước.</p>
          )}
        </div>
        {venue && (
          <div className="rounded-lg border bg-surface-alt p-4 text-sm">
            <p className="font-medium">{venue.name}</p>
            <p className="text-muted-foreground">
              {venue.address}, {venue.city}
            </p>
            {venue.capacity ? <p className="mt-1 text-muted-foreground">Sức chứa {venue.capacity} người</p> : null}
          </div>
        )}
      </div>
    </EventSectionCard>
  );
}
