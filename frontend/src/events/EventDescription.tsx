import DOMPurify from 'dompurify';
import { useMemo } from 'react';

/** Định dạng mô tả được phép; còn lại (script, style, ảnh, iframe…) bị bỏ. */
const ALLOWED_TAGS = ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 's', 'mark', 'code', 'a', 'ul', 'ol', 'li', 'h3', 'h4', 'blockquote', 'hr'];
const ALLOWED_ATTR = ['href'];

// Liên kết trong mô tả luôn mở tab mới và không truy cập được trang này.
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank');
    node.setAttribute('rel', 'noopener noreferrer');
  }
});

const HTML_TAG = /<\/?[a-z][^>]*>/i;

// Tailwind preflight xóa định dạng danh sách, tiêu đề, liên kết nên khôi phục lại ở đây.
const RICH_TEXT_CLASSES = [
  'leading-relaxed [&>*+*]:mt-3',
  '[&_strong]:font-semibold [&_b]:font-semibold [&_em]:italic [&_i]:italic [&_u]:underline [&_s]:line-through',
  '[&_mark]:rounded-sm [&_mark]:bg-primary/15 [&_mark]:px-1 [&_mark]:text-foreground',
  '[&_code]:rounded [&_code]:bg-muted [&_code]:px-1.5 [&_code]:py-0.5 [&_code]:font-mono [&_code]:text-sm',
  '[&_a]:font-medium [&_a]:break-all [&_a]:text-primary [&_a]:underline [&_a]:underline-offset-2',
  '[&_ul]:list-disc [&_ul]:pl-6 [&_ol]:list-decimal [&_ol]:pl-6 [&_li]:mt-1',
  '[&_h3]:pt-2 [&_h3]:text-lg [&_h3]:font-bold [&_h4]:font-semibold',
  '[&_blockquote]:border-l-4 [&_blockquote]:border-primary/40 [&_blockquote]:pl-4 [&_blockquote]:text-muted-foreground [&_blockquote]:italic',
  '[&_hr]:my-6 [&_hr]:border-border',
].join(' ');

/**
 * Mô tả sự kiện theo openevent: có thẻ HTML thì lọc qua DOMPurify rồi hiển thị, không thì là văn bản thường giữ xuống
 * dòng (mô tả tạo trước khi có trình soạn thảo).
 */
export function EventDescription({ description }: { description: string | null }) {
  const html = useMemo(
    () =>
      description && HTML_TAG.test(description) ? DOMPurify.sanitize(description, { ALLOWED_TAGS, ALLOWED_ATTR }) : null,
    [description],
  );

  if (!description) return <p className="text-muted-foreground">Chưa có mô tả.</p>;
  if (html !== null) {
    // An toàn: html đã qua DOMPurify với danh sách thẻ và thuộc tính cho phép.
    return <div className={RICH_TEXT_CLASSES} dangerouslySetInnerHTML={{ __html: html }} />;
  }
  return <p className="leading-relaxed wrap-break-word whitespace-pre-wrap">{description}</p>;
}
