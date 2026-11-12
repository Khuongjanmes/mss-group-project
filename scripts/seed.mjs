// Dữ liệu mẫu cho demo: tài khoản, danh mục, địa điểm, tổ chức và sự kiện ở đủ trạng thái.
// Gọi API qua Gateway nên dữ liệu đi đúng quy tắc nghiệp vụ. Chạy lại không tạo trùng (so theo email, tên, tiêu đề).
//
//   docker compose up -d --build        # chờ các service healthy
//   node scripts/seed.mjs               # Node 18+; đọc BOOTSTRAP_ADMIN_* trong .env
//
// Đổi Gateway: GATEWAY_URL=http://host:8080 node scripts/seed.mjs

import { readFileSync } from 'node:fs';

const GATEWAY = process.env.GATEWAY_URL ?? 'http://localhost:8080';
const ORGANIZER_PASSWORD = 'Organizer@123';
const STUDENT_PASSWORD = 'Student@123';

const env = Object.fromEntries(
  readFileSync(new URL('../.env', import.meta.url), 'utf8')
    .split(/\r?\n/)
    .filter((line) => /^[A-Z_]+=/.test(line))
    .map((line) => {
      const [key, ...value] = line.split('=');
      return [key, value.join('=').trim().replace(/^["']|["']$/g, '')];
    }),
);

async function call(method, path, token, body) {
  const res = await fetch(GATEWAY + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) },
    body: body && JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, data: text ? JSON.parse(text) : null };
}

/** Gọi API, lỗi thì dừng script với thông báo rõ ràng. */
async function must(method, path, token, body, ok = [200, 201, 204]) {
  const res = await call(method, path, token, body);
  if (!ok.includes(res.status)) {
    throw new Error(`${method} ${path} → ${res.status} ${res.data?.code ?? ''} ${res.data?.message ?? ''}`);
  }
  return res.data;
}

async function login(email, password) {
  const res = await call('POST', '/api/auth/login', null, { email, password });
  if (res.status !== 200) throw new Error(`Không đăng nhập được ${email} (${res.status} ${res.data?.code ?? ''})`);
  return res.data.accessToken;
}

/** Đăng ký nếu chưa có (409 nghĩa là đã có), rồi đăng nhập. */
async function account(email, fullName, password) {
  const res = await call('POST', '/api/auth/register', null, { email, password, fullName });
  if (res.status !== 201 && res.status !== 409) {
    throw new Error(`Đăng ký ${email} → ${res.status} ${res.data?.code ?? ''} ${res.data?.message ?? ''}`);
  }
  console.log(`  ${res.status === 201 ? 'tạo' : 'đã có'}  ${email}`);
  return login(email, password);
}

// ---------- Tài khoản ----------
console.log('Tài khoản');
if (!env.BOOTSTRAP_ADMIN_EMAIL || !env.BOOTSTRAP_ADMIN_PASSWORD) {
  throw new Error('Thiếu BOOTSTRAP_ADMIN_EMAIL hoặc BOOTSTRAP_ADMIN_PASSWORD trong .env');
}
const admin = await login(env.BOOTSTRAP_ADMIN_EMAIL, env.BOOTSTRAP_ADMIN_PASSWORD);
const organizer = await account('organizer@openevent.local', 'Nguyễn Văn Tổ Chức', ORGANIZER_PASSWORD);
await account('manager@openevent.local', 'Trần Thị Quản Lý', ORGANIZER_PASSWORD);
await account('student1@openevent.local', 'Lê Minh Sinh Viên', STUDENT_PASSWORD);
await account('student2@openevent.local', 'Phạm Thu Sinh Viên', STUDENT_PASSWORD);

// ---------- Danh mục ----------
console.log('Danh mục');
const CATEGORIES = [
  ['Hội thảo', 'Hội thảo chuyên đề, talkshow với khách mời'],
  ['Âm nhạc', 'Đêm nhạc, liveshow, acoustic'],
  ['Thể thao', 'Giải đấu, chạy bộ, hoạt động thể chất'],
  ['Workshop', 'Buổi thực hành theo nhóm nhỏ'],
  ['Triển lãm', 'Trưng bày sản phẩm, dự án, nghệ thuật'],
];
const categories = new Map((await must('GET', '/api/categories')).map((c) => [c.name, c.id]));
for (const [name, description] of CATEGORIES) {
  if (!categories.has(name)) {
    categories.set(name, (await must('POST', '/api/categories', admin, { name, description })).id);
    console.log(`  tạo    ${name}`);
  }
}

// ---------- Địa điểm ----------
console.log('Địa điểm');
const VENUES = [
  { name: 'Hội trường FPT Đà Nẵng', address: 'Khu đô thị FPT City, Ngũ Hành Sơn', city: 'Đà Nẵng', capacity: 500 },
  { name: 'Phòng Alpha 201', address: 'Tòa Alpha, Đại học FPT Đà Nẵng', city: 'Đà Nẵng', capacity: 60 },
  { name: 'Sân vận động FPT', address: 'Khu thể thao, Đại học FPT Đà Nẵng', city: 'Đà Nẵng', capacity: 2000 },
  { name: 'Nhà văn hóa Thanh niên', address: '2 Trần Phú, Hải Châu', city: 'Đà Nẵng', capacity: 800 },
];
const venues = new Map((await must('GET', '/api/venues?size=100')).content.map((v) => [v.name, v.id]));
for (const venue of VENUES) {
  if (!venues.has(venue.name)) {
    venues.set(venue.name, (await must('POST', '/api/venues', admin, venue)).id);
    console.log(`  tạo    ${venue.name}`);
  }
}

// ---------- Tổ chức ----------
console.log('Tổ chức');
const ORG = 'FPT Event Club';
let org = (await must('GET', '/api/organizations/my', organizer)).find((o) => o.name === ORG);
if (!org) {
  org = await must('POST', '/api/organizations', organizer, {
    name: ORG,
    description: 'Câu lạc bộ tổ chức sự kiện của sinh viên Đại học FPT Đà Nẵng.',
  });
  console.log(`  tạo    ${ORG}`);
}
const members = await must('GET', `/api/organizations/${org.id}/members`, organizer);
if (!members.some((m) => m.email === 'manager@openevent.local')) {
  await must('POST', `/api/organizations/${org.id}/members`, organizer, { email: 'manager@openevent.local', role: 'MANAGER' });
  console.log('  mời    manager@openevent.local làm MANAGER');
}

// ---------- Sự kiện ----------
console.log('Sự kiện');
const DAY = 864e5;
/** Ngày `daysAhead` tính từ hôm nay, lúc `hour` giờ Việt Nam (UTC+7). */
const at = (daysAhead, hour) => {
  const d = new Date(Date.now() + daysAhead * DAY);
  d.setUTCHours(hour - 7, 0, 0, 0);
  return d.toISOString();
};
const html = (intro, points) => `<p>${intro}</p><ul>${points.map((p) => `<li><p>${p}</p></li>`).join('')}</ul>`;

const EVENTS = [
  {
    title: 'Hội thảo AI trong phát triển phần mềm 2026',
    category: 'Hội thảo', venue: 'Hội trường FPT Đà Nẵng', days: 7, hour: 8, hours: 4, org: true, status: 'PUBLISHED',
    description: html('Chuyên gia từ các công ty công nghệ chia sẻ cách dùng AI trong quy trình phát triển phần mềm.',
      ['Xu hướng AI coding assistant', 'Kiểm thử và review code với AI', 'Hỏi đáp cùng diễn giả']),
  },
  {
    title: 'Đêm nhạc Acoustic Mùa Thu',
    category: 'Âm nhạc', venue: 'Nhà văn hóa Thanh niên', days: 10, hour: 19, hours: 3, org: true, status: 'PUBLISHED',
    description: html('Đêm nhạc acoustic do các ban nhạc sinh viên biểu diễn.', ['Ban nhạc khách mời', 'Mini game có quà', 'Gian hàng đồ uống']),
  },
  {
    title: 'Giải chạy FPT Run 5K',
    category: 'Thể thao', venue: 'Sân vận động FPT', days: 14, hour: 6, hours: 3, org: true, status: 'PUBLISHED',
    description: html('Giải chạy bộ 5km quanh khuôn viên trường, có huy chương cho người hoàn thành.', ['Cự ly 5km', 'Áo và bib cho mỗi vận động viên', 'Huy chương hoàn thành']),
  },
  {
    title: 'Workshop React và Spring Boot cho người mới',
    category: 'Workshop', venue: 'Phòng Alpha 201', days: 5, hour: 14, hours: 3, org: false, status: 'PUBLISHED',
    description: html('Thực hành xây dựng ứng dụng web nhỏ với React và Spring Boot.', ['Mang theo laptop', 'Cài sẵn JDK 21 và Node 20', 'Có mentor hỗ trợ']),
  },
  {
    title: 'Triển lãm Dự án Capstone',
    category: 'Triển lãm', venue: 'Hội trường FPT Đà Nẵng', days: 21, hour: 9, hours: 6, org: true, status: 'PUBLISHED',
    description: html('Sinh viên năm cuối trưng bày và demo đồ án tốt nghiệp.', ['Hơn 40 dự án', 'Bình chọn dự án yêu thích', 'Gặp gỡ doanh nghiệp tuyển dụng']),
  },
  {
    title: 'Talkshow Khởi nghiệp sinh viên',
    category: 'Hội thảo', venue: 'Nhà văn hóa Thanh niên', days: 28, hour: 18, hours: 2, org: false, status: 'PUBLISHED',
    description: html('Những founder trẻ kể về hành trình khởi nghiệp từ ghế giảng đường.', ['Gọi vốn lần đầu', 'Sai lầm thường gặp', 'Kết nối mentor']),
  },
  {
    title: 'Hackathon OpenEvent 24h',
    category: 'Workshop', venue: 'Hội trường FPT Đà Nẵng', days: 35, hour: 8, hours: 24, org: true, status: 'PENDING_APPROVAL',
    description: html('Cuộc thi lập trình 24 giờ theo đội 3–5 người.', ['Đề bài công bố lúc khai mạc', 'Giải thưởng cho 3 đội đứng đầu']),
  },
  {
    title: 'Ngày hội Câu lạc bộ (bản nháp)',
    category: 'Triển lãm', venue: 'Sân vận động FPT', days: 40, hour: 8, hours: 8, org: true, status: 'DRAFT',
    description: html('Các câu lạc bộ giới thiệu hoạt động và tuyển thành viên mới.', ['Gian hàng từng câu lạc bộ']),
  },
];

const mine = new Map((await must('GET', '/api/events/my?size=100', organizer)).content.map((e) => [e.title, e]));
for (const [i, e] of EVENTS.entries()) {
  if (mine.has(e.title)) {
    console.log(`  đã có  ${e.title} (${mine.get(e.title).status})`);
    continue;
  }
  const created = await must('POST', '/api/events', organizer, {
    title: e.title,
    description: e.description,
    bannerUrl: `https://picsum.photos/seed/openevent-${i + 1}/1200/500`,
    categoryId: categories.get(e.category),
    venueId: venues.get(e.venue),
    startTime: at(e.days, e.hour),
    endTime: new Date(Date.parse(at(e.days, e.hour)) + e.hours * 36e5).toISOString(),
    organizationId: e.org ? org.id : null,
  });
  if (e.status !== 'DRAFT') await must('PUT', `/api/events/${created.id}/submit`, organizer);
  if (e.status === 'PUBLISHED') await must('PUT', `/api/events/${created.id}/approve`, admin);
  console.log(`  tạo    ${e.title} (${e.status})`);
}

console.log(`
Xong. Tài khoản mẫu:
  organizer@openevent.local  ${ORGANIZER_PASSWORD}  OWNER của "${ORG}", HOST các sự kiện mẫu: vào /organizer để tạo loại vé
  manager@openevent.local    ${ORGANIZER_PASSWORD}  MANAGER của "${ORG}"
  student1@openevent.local   ${STUDENT_PASSWORD}    người mua vé
  student2@openevent.local   ${STUDENT_PASSWORD}    người mua vé
  ${env.BOOTSTRAP_ADMIN_EMAIL}      (mật khẩu trong .env)  Admin`);
