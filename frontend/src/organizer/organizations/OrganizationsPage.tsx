import { ArrowRight, Building2, Plus, Users } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { errorMessage } from '@/api/client';
import { listMyOrganizations, type Organization, ORGANIZATION_ROLE_LABELS } from '@/api/organizations';
import { EmptyState, ErrorState, LoadingState } from '@/components/common/feedback';
import { PageHeader } from '@/components/common/layout';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { OrganizationFormDialog } from './OrganizationFormDialog';
import { ROLE_BADGE } from './organizationDisplay';

/** Tổ chức mình tham gia, kèm vai trò của mình trong từng tổ chức. */
export default function OrganizationsPage() {
  const navigate = useNavigate();
  const [organizations, setOrganizations] = useState<Organization[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    let ignore = false;
    setError(null);
    listMyOrganizations()
      .then((data) => !ignore && setOrganizations(data))
      .catch((err: unknown) => !ignore && setError(errorMessage(err)));
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  const createButton = (
    <Button onClick={() => setCreating(true)} className="h-9 rounded-lg px-3 font-semibold">
      <Plus />
      Tạo tổ chức
    </Button>
  );

  return (
    <div className="text-sm">
      <PageHeader
        title="Tổ chức của tôi"
        description="Câu lạc bộ, đơn vị bạn tham gia. Thành viên cùng tổ chức cùng quản lý sự kiện theo vai trò."
        actions={createButton}
      />

      {error ? (
        <ErrorState description={error} onRetry={() => setReloadKey((k) => k + 1)} />
      ) : !organizations ? (
        <LoadingState lines={4} />
      ) : organizations.length === 0 ? (
        <div className="rounded-xl border bg-card shadow-sm">
          <EmptyState
            title="Bạn chưa tham gia tổ chức nào"
            description="Tạo tổ chức để mời bạn bè cùng quản lý sự kiện, hoặc nhờ Chủ sở hữu một tổ chức mời bạn."
            action={createButton}
          />
        </div>
      ) : (
        <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {organizations.map((organization) => (
            <li key={organization.id}>
              <div className="group relative flex h-full flex-col gap-3 rounded-xl border bg-card p-5 shadow-xs transition focus-within:ring-2 focus-within:ring-ring/50 hover:-translate-y-0.5 hover:shadow-md motion-reduce:hover:translate-y-0">
                <div className="flex items-start gap-3">
                  <div className="flex size-11 shrink-0 items-center justify-center rounded-lg bg-linear-to-br from-accent to-surface-alt text-accent-foreground">
                    <Building2 className="size-5" aria-hidden />
                  </div>
                  <div className="min-w-0 flex-1">
                    <h2 className="line-clamp-1 text-base font-semibold transition-colors group-hover:text-primary">
                      <Link
                        to={`/organizer/organizations/${organization.id}`}
                        className="after:absolute after:inset-0 after:rounded-xl focus-visible:outline-none"
                      >
                        {organization.name}
                      </Link>
                    </h2>
                    <Badge variant={ROLE_BADGE[organization.myRole]} size="label" className="mt-1">
                      {ORGANIZATION_ROLE_LABELS[organization.myRole]}
                    </Badge>
                  </div>
                </div>
                <p className="line-clamp-2 text-muted-foreground">{organization.description || 'Chưa có mô tả.'}</p>
                <div className="mt-auto flex items-center justify-between gap-3 border-t pt-3 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1.5">
                    <Users className="size-3.5" aria-hidden />
                    {organization.memberCount} thành viên
                  </span>
                  <span className="flex items-center gap-1 text-sm font-medium text-accent-foreground">
                    Quản lý
                    <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden />
                  </span>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      <OrganizationFormDialog
        open={creating}
        onOpenChange={setCreating}
        onSaved={(created) => navigate(`/organizer/organizations/${created.id}`)}
      />
    </div>
  );
}
