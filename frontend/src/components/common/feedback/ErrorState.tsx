import { Button } from '@/components/ui/button';

type ErrorStateProps = {
  title?: string;
  description?: string;
  /** Shows a retry button when provided. */
  onRetry?: () => void;
};

export function ErrorState({
  title = 'Đã có lỗi xảy ra',
  description = 'Vui lòng thử lại sau.',
  onRetry,
}: ErrorStateProps) {
  return (
    <div role="alert" className="flex flex-col items-center gap-2 py-12 text-center">
      <p className="font-medium">{title}</p>
      <p className="text-sm text-muted-foreground">{description}</p>
      {onRetry && (
        <Button variant="outline" className="mt-2" onClick={onRetry}>
          Thử lại
        </Button>
      )}
    </div>
  );
}
