import { Building2, CalendarDays, Pencil, Plus, Users } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';

import { errorMessage } from '@/api/client';
import type { EventItem, EventStatus } from '@/api/events';
import {
  getOrganization,
  listOrganizationEvents,
  type Organization,
  ORGANIZATION_ROLE_LABELS,
} from '@/api/organizations';
import { useSession } from '@/auth/session';
import { ErrorState, LoadingState } from '@/components/common/feedback';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EventListPage, type EventQuery } from '@/events/EventListPage';
import { cn } from '@/lib/utils';
import { CreateEventModal } from '../CreateEventModal';
import { MembersPanel } from './MembersPanel';
import { OrganizationFormDialog } from './OrganizationFormDialog';
import { ROLE_BADGE, ROLE_DESCRIPTIONS } from './organizationDisplay';

const STATUSES: EventStatus[] = ['DRAFT', 'PENDING_APPROVAL', 'REJECTED', 'PUBLISHED', 'CANCELLED', 'COMPLETED'];

const TABS = [
  { value: 'events', label: 'Sự kiện', icon: CalendarDays },
  { value: 'members', label: 'Thành viên', icon: Users },
] as const;

type Tab = (typeof TABS)[number]['value'];

export default function OrganizationDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const session = useSession();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab: Tab = searchParams.get('tab') === 'members' ? 'members' : 'events';
  const [organization, setOrganization] = useState<Organization | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [editing, setEditing] = useState(false);
  const [creatingEvent, setCreatingEvent] = useState(false);

  useEffect(() => {
    let ignore = false;
    setError(null);
    getOrganization(id)
      .then((data) => !ignore && setOrganization(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [id, reloadKey]);

  const loadEvents = useCallback(
    ({ status, page, size }: EventQuery) => listOrganizationEvents(id, { status, page, size }),
    [id],
  );

  if (error) {
    return <ErrorState title="Không thể tải tổ chức" description={error} onRetry={() => setReloadKey((k) => k + 1)} />;
  }
  if (!organization) return <LoadingState lines={8} />;

  // MEMBER chỉ thao tác trên sự kiện mình làm HOST (BR-06).
  const canManage = (event: EventItem) => organization.myRole !== 'MEMBER' || event.organizerId === session?.user.id;

  return (
    <div className="space-y-6 text-sm">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div className="flex min-w-0 items-start gap-4">
          <div className="flex size-14 shrink-0 items-center justify-center rounded-xl bg-linear-to-br from-accent to-surface-alt text-accent-foreground">
            <Building2 className="size-6" aria-hidden />
          </div>
          <div className="min-w-0 space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-bold tracking-tight">{organization.name}</h1>
              <Badge variant={ROLE_BADGE[organization.myRole]} size="label">
                {ORGANIZATION_ROLE_LABELS[organization.myRole]}
              </Badge>
            </div>
            {organization.description && (
              <p className="whitespace-pre-line text-muted-foreground">{organization.description}</p>
            )}
            <p className="text-xs text-muted-foreground">Quyền của bạn: {ROLE_DESCRIPTIONS[organization.myRole]}</p>
          </div>
        </div>
        {organization.myRole === 'OWNER' && (
          <Button variant="outline" onClick={() => setEditing(true)} className="h-9 shrink-0 self-start rounded-lg">
            <Pencil />
            Sửa tổ chức
          </Button>
        )}
      </div>

      <div className="flex w-fit items-center gap-1 rounded-lg border bg-surface-alt p-1" role="tablist">
        {TABS.map(({ value, label, icon: Icon }) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={tab === value}
            onClick={() => setSearchParams(value === 'events' ? {} : { tab: value }, { replace: true })}
            className={cn(
              'flex h-8 items-center gap-1.5 rounded-md px-3 text-sm transition-colors',
              tab === value
                ? 'bg-card font-semibold text-accent-foreground shadow-sm'
                : 'font-medium text-muted-foreground hover:text-foreground',
            )}
          >
            <Icon className="size-4" aria-hidden />
            {label}
            {value === 'members' && <span className="text-xs text-muted-foreground">({organization.memberCount})</span>}
          </button>
        ))}
      </div>

      {tab === 'events' ? (
        <EventListPage
          key={organization.id}
          area="organizer"
          load={loadEvents}
          statuses={STATUSES}
          canManage={canManage}
          emptyTitle="Tổ chức chưa có sự kiện nào"
          emptyDescription={
            organization.canCreateEvents
              ? 'Bấm "Tạo sự kiện" để tạo sự kiện đầu tiên của tổ chức.'
              : 'Bạn chưa được cấp quyền tạo sự kiện cho tổ chức này.'
          }
          headerActions={
            organization.canCreateEvents && (
              <Button onClick={() => setCreatingEvent(true)} className="h-9 rounded-lg px-3 font-semibold">
                <Plus />
                Tạo sự kiện
              </Button>
            )
          }
        />
      ) : (
        <MembersPanel
          organization={organization}
          onOrganizationChanged={(left) =>
            left ? navigate('/organizer/organizations', { replace: true }) : setReloadKey((k) => k + 1)
          }
        />
      )}

      <OrganizationFormDialog
        open={editing}
        onOpenChange={setEditing}
        organization={organization}
        onSaved={setOrganization}
      />
      <CreateEventModal
        open={creatingEvent}
        onOpenChange={setCreatingEvent}
        defaultOrganizationId={organization.id}
      />
    </div>
  );
}
