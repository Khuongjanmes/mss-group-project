/** Error under a field of the edit event form; renders nothing without a message. */
export function FieldErrorText({ id, message }: { id: string; message?: string }) {
  return message ? (
    <p id={id} role="alert" className="mt-1.5 text-xs font-medium text-destructive">
      {message}
    </p>
  ) : null;
}
