import { Skeleton } from '@/components/ui/skeleton';

type LoadingStateProps = {
  /** Number of skeleton lines to render. */
  lines?: number;
};

export function LoadingState({ lines = 3 }: LoadingStateProps) {
  return (
    <div role="status" aria-label="Đang tải" className="space-y-3">
      {Array.from({ length: lines }, (_, index) => (
        <Skeleton key={index} className="h-5 w-full last:w-2/3" />
      ))}
    </div>
  );
}
