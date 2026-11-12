/** Field label text of the edit event form. */
export const LABEL_TEXT_CLASS = 'text-[11px] font-bold tracking-wider text-foreground uppercase';

/** Field label above an input. */
export const FIELD_LABEL_CLASS = `mb-2 block ${LABEL_TEXT_CLASS}`;

/** Text inputs and select triggers share one look so they line up side by side. */
export const FIELD_CLASS =
  'h-11 w-full rounded-lg border-input bg-background text-[14px] shadow-xs focus-visible:bg-card';

/**
 * shadcn's `SelectTrigger` sets its height through `data-[size=default]:h-9`, which beats a plain `h-11`,
 * so the trigger needs the same variant to match the inputs.
 */
export const SELECT_TRIGGER_CLASS = `${FIELD_CLASS} px-4 data-[size=default]:h-11`;
