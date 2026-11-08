import type { Blocker } from 'react-router';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';

/** Confirms leaving a page whose form has unsaved changes (pair with `useUnsavedChangesGuard`). */
export function UnsavedChangesDialog({ blocker }: { blocker: Blocker }) {
  return (
    <AlertDialog
      open={blocker.state === 'blocked'}
      onOpenChange={(open) => {
        if (!open && blocker.state === 'blocked') blocker.reset();
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Rời trang khi chưa lưu?</AlertDialogTitle>
          <AlertDialogDescription>
            Trang này có thay đổi chưa lưu. Rời trang thì các thay đổi này sẽ mất.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Ở lại để lưu</AlertDialogCancel>
          <AlertDialogAction variant="destructive" onClick={() => blocker.proceed?.()}>
            Rời trang
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
