import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import type { CurrentUser } from './types';

type UserAvatarProps = {
  user: CurrentUser;
};

export function UserAvatar({ user }: UserAvatarProps) {
  // Vietnamese names put the given name last: "Nguyễn Quang Minh" → "M".
  const initial = user.name.trim().split(/\s+/).at(-1)?.charAt(0).toUpperCase() ?? '?';

  return (
    <Avatar className="size-8 rounded-lg">
      {user.avatarUrl ? <AvatarImage src={user.avatarUrl} alt={user.name} /> : null}
      <AvatarFallback className="rounded-lg bg-accent text-accent-foreground">
        {initial}
      </AvatarFallback>
    </Avatar>
  );
}
