import { useEffect, useState } from 'react';

import { listMyOrganizations, type Organization } from '@/api/organizations';

/**
 * Tổ chức mình tham gia, để biết quyền trên sự kiện của tổ chức (BR-06). Trả mảng rỗng khi đang tải hoặc lỗi:
 * lúc đó chỉ sự kiện mình làm HOST hiện nút thao tác, server vẫn kiểm tra lại quyền.
 */
export function useMyOrganizations(): Organization[] {
  const [organizations, setOrganizations] = useState<Organization[]>([]);

  useEffect(() => {
    let ignore = false;
    listMyOrganizations()
      .then((data) => !ignore && setOrganizations(data))
      .catch(() => undefined);
    return () => {
      ignore = true;
    };
  }, []);

  return organizations;
}
