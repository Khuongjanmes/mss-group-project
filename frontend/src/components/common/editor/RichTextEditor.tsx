import { EditorContent, type Editor, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import {
  Bold,
  Code,
  Italic,
  Link as LinkIcon,
  List,
  ListOrdered,
  Quote,
  Underline as UnderlineIcon,
} from 'lucide-react';
import { useEffect } from 'react';

import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

// StarterKit v3 đã gồm Underline và Link.
const extensions = [StarterKit.configure({ link: { openOnClick: false } })];

type RichTextEditorProps = {
  value: string;
  onChange: (html: string) => void;
  /** Tên của ô soạn thảo cho trình đọc màn hình. */
  label: string;
  id?: string;
  disabled?: boolean;
  invalid?: boolean;
};

/** Ô mô tả có định dạng (đậm, nghiêng, danh sách, liên kết, trích dẫn) lưu dạng HTML, theo openevent. */
export function RichTextEditor({ value, onChange, label, id, disabled = false, invalid = false }: RichTextEditorProps) {
  const editor = useEditor({
    extensions,
    content: value,
    editable: !disabled,
    editorProps: {
      attributes: {
        ...(id ? { id } : {}),
        'aria-label': label,
        'aria-multiline': 'true',
        role: 'textbox',
        class:
          'min-h-[150px] bg-card p-5 text-[14px] leading-relaxed text-foreground outline-none [&_a]:text-primary [&_a]:underline [&_blockquote]:border-l-2 [&_blockquote]:pl-4 [&_blockquote]:italic [&_code]:rounded [&_code]:bg-muted [&_code]:px-1 [&_ol]:list-decimal [&_ol]:pl-6 [&_p]:my-2 [&_ul]:list-disc [&_ul]:pl-6',
      },
    },
    onUpdate: ({ editor: updated }) => onChange(updated.isEmpty ? '' : updated.getHTML()),
  });

  // Giá trị nạp hoặc đặt lại từ ngoài thay nội dung mà không tính là người dùng sửa.
  useEffect(() => {
    if (editor && editor.getHTML() !== (value || '<p></p>')) {
      editor.commands.setContent(value || '', { emitUpdate: false });
    }
  }, [editor, value]);

  useEffect(() => {
    editor?.setEditable(!disabled);
  }, [editor, disabled]);

  const items = [
    { label: 'In đậm', icon: Bold, mark: 'bold', run: (e: Editor) => e.chain().focus().toggleBold().run() },
    { label: 'In nghiêng', icon: Italic, mark: 'italic', run: (e: Editor) => e.chain().focus().toggleItalic().run() },
    { label: 'Gạch chân', icon: UnderlineIcon, mark: 'underline', run: (e: Editor) => e.chain().focus().toggleUnderline().run() },
    { label: 'Danh sách', icon: List, mark: 'bulletList', run: (e: Editor) => e.chain().focus().toggleBulletList().run() },
    {
      label: 'Danh sách đánh số',
      icon: ListOrdered,
      mark: 'orderedList',
      run: (e: Editor) => e.chain().focus().toggleOrderedList().run(),
    },
    { label: 'Thêm liên kết', icon: LinkIcon, mark: 'link', run: addLink },
    { label: 'Trích dẫn', icon: Quote, mark: 'blockquote', run: (e: Editor) => e.chain().focus().toggleBlockquote().run() },
    { label: 'Code', icon: Code, mark: 'code', run: (e: Editor) => e.chain().focus().toggleCode().run() },
  ];

  return (
    <div
      aria-invalid={invalid || undefined}
      className={cn(
        'overflow-hidden rounded-lg border border-input bg-card shadow-xs focus-within:border-ring focus-within:ring-[3px] focus-within:ring-ring/50',
        invalid && 'border-destructive',
        disabled && 'opacity-60',
      )}
    >
      <div className="flex flex-wrap items-center gap-1 border-b border-input bg-accent/60 px-3 py-2" role="toolbar" aria-label="Định dạng">
        {items.map(({ label: itemLabel, icon: Icon, mark, run }) => {
          const active = editor?.isActive(mark) ?? false;
          return (
            <Button
              key={itemLabel}
              type="button"
              variant="ghost"
              size="sm"
              aria-label={itemLabel}
              aria-pressed={active}
              title={itemLabel}
              disabled={!editor || disabled}
              // Giữ vùng chọn trong ô soạn thảo khi bấm nút.
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => editor && run(editor)}
              className={cn('size-8 p-0 text-foreground hover:bg-primary/10', active && 'bg-primary/15 text-primary')}
            >
              <Icon className="size-4" />
            </Button>
          );
        })}
      </div>
      <EditorContent editor={editor} />
    </div>
  );
}

function addLink(editor: Editor) {
  const previous = editor.getAttributes('link').href as string | undefined;
  const href = window.prompt('Nhập URL liên kết (để trống để bỏ liên kết)', previous ?? 'https://');
  if (href === null) return;
  if (!href.trim()) {
    editor.chain().focus().extendMarkRange('link').unsetLink().run();
    return;
  }
  // Chỉ nhận http(s) để không chèn được javascript: vào mô tả.
  if (!/^https?:\/\//i.test(href.trim())) {
    window.alert('Liên kết phải bắt đầu bằng http:// hoặc https://');
    return;
  }
  editor.chain().focus().extendMarkRange('link').setLink({ href: href.trim() }).run();
}
