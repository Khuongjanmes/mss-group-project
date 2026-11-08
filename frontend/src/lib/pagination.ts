const numberFormatter = new Intl.NumberFormat('vi-VN');

/** Number with Vietnamese grouping: `1.234`. */
export function formatNumber(value: number): string {
  return numberFormatter.format(value);
}

/**
 * 0-based page indexes to show: first, last and a window around the current page, with `null` for gaps.
 */
export function pageItems(current: number, total: number): (number | null)[] {
  const pages = new Set([0, total - 1, current - 1, current, current + 1]);
  const sorted = [...pages].filter((page) => page >= 0 && page < total).sort((a, b) => a - b);
  const items: (number | null)[] = [];
  sorted.forEach((page, index) => {
    if (index > 0 && page - sorted[index - 1] > 1) items.push(null);
    items.push(page);
  });
  return items;
}
