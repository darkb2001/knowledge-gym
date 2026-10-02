# Knowledge Gym — Khảo sát trước redesign

Ngày khảo sát: 2026-10-02. Các mục 1–7 lưu baseline trước triển khai, không phải trạng thái hiện tại. Sau đó chủ sản phẩm giao quyền quyết định và cho phép chạy trực tiếp. Kết quả triển khai/kiểm chứng ở mục 8 và `VERIFICATION.md`.

## 1. Sản phẩm được thể hiện trong repository

Nguồn (đường dẫn từ root repository `knowledge-gym`): `docs/00-overview.md`, `docs/01-ux-modes.md` và các route frontend. Tài liệu roadmap không đồng nghĩa với tính năng đã hoàn thành.

Knowledge Gym là nền tảng ôn tập kiến thức IT, có nội dung Java và định hướng mở rộng Spring, AWS, Kubernetes. Các cơ chế thể hiện trong mã nguồn: đọc/tìm câu hỏi, flashcard với spaced repetition, quiz, mock interview, ghi chú, mindmap, dashboard tiến độ và blog. Có các trang quản trị writer và search.

Giả thuyết cần chủ sản phẩm xác nhận: người dùng chính là lập trình viên ôn kiến thức/phỏng vấn và xây thói quen học. Chưa xác nhận cấp độ ưu tiên, ngữ cảnh sử dụng chính và ngôn ngữ giao diện dài hạn.

### Stack thực tế

Next.js 14.2.35, React 18, TypeScript, Tailwind 3.4.1, Vitest, ESLint. `package.json` hiện không có shadcn/Radix/Motion/icon library dù overview có nhắc shadcn. Có font Geist/Geist Mono local nhưng CSS hiện tải Fraunces/Source Sans 3/IBM Plex Mono từ Google Fonts.

### Bề mặt hiện có

- `/` chuyển sang `/questions`; chưa có landing page độc lập.
- Auth: `/login`, `/register`, `/forgot-password`.
- Học: `/questions`, `/questions/[id]`, `/flashcard/[moduleId]`, `/quiz/[moduleId]`, `/mock-interview`.
- Theo dõi và hỗ trợ: `/dashboard`, `/notes`, `/mindmap`, `/blog`, `/blog/[slug]`.
- Quản trị: `/admin/writer`, `/admin/search`.

## 2. Impeccable detect — kết quả có thể kiểm chứng

Lệnh chạy tại `kg-frontend`:

```sh
/Users/bao2k1/Documents/javaNote/.pi/skills/impeccable/scripts/impeccable detect --json app components tailwind.config.ts
```

Kết quả raw: `impeccable-baseline.json`. Exit code **2**: có finding, không phải scanner thất bại. Một warning `overused-font` ở `app/globals.css:1`, liên quan Fraunces.

Đây là baseline theo yêu cầu, không phải quality gate sau implementation. Detector không hiểu mục tiêu học tập, không chứng minh accessibility/responsiveness đạt chuẩn và không thay thế review bằng trình duyệt. Không tự động bỏ một font chỉ vì warning nếu sau này có lý do thương hiệu được xác nhận.

## 3. Vấn đề UX/visual quan sát từ mã nguồn

| Vấn đề | Bằng chứng | Hướng giải quyết cần thiết kế |
| --- | --- | --- |
| Navigation thiếu cấu trúc học tập | `AppHeader` chủ yếu có Dashboard, tên người dùng và logout; các liên kết khác nằm ở dashboard | App shell nhất quán, nhóm Học / Tiến độ / Thư viện; admin tách khỏi tác vụ người học |
| Dashboard thiên về báo cáo hơn hành động | Radar, heatmap và leaderboard đứng ngang trọng lượng; chưa có tác vụ tiếp tục học rõ ràng ở phần đầu | Xác định hành động chính khi quay lại; chỉ dùng dữ liệu API thực sự cung cấp |
| Thương hiệu lặp mà tiêu đề tác vụ chưa rõ | `/questions` lặp Knowledge Gym dưới header | Tiêu đề theo công việc: Thư viện câu hỏi; thương hiệu nằm ở shell |
| Copy dành cho developer thay vì người học | “full-text”, “đáp án đã sanitize”, “catalog”, “slug section…” | Copy Việt rõ nghĩa; giữ từ kỹ thuật khi nó là nội dung học, không phải chi tiết triển khai |
| Visual world chưa hợp giao diện thao tác | Fraunces serif + nền green/ember nhiều gradient, border và nền bán trong suốt | Hệ chữ đọc lâu, hierarchy bình tĩnh, nhận diện có chủ đích thay vì trang trí phủ toàn app |
| Theme khó mở rộng | Màu `ink`, `moss`, `ember` và opacity gắn trực tiếp trong nhiều route | Semantic tokens cho background/surface/text/border/action/status; không đảo toàn bộ ink scale một cách mù quáng |
| Dashboard có nguy cơ tràn ngang | Cụm action bên trong dùng flex không wrap, nhiều liên kết và streak | Navigation responsive có ưu tiên; kiểm tra desktop/mobile thực tế |
| Motion chưa có chính sách giảm chuyển động | `soft-pulse` lặp vô hạn; chưa thấy reduced-motion trong global CSS được khảo sát | Motion chỉ phản hồi trạng thái, tôn trọng reduced-motion |
| Nội dung dài cần được bảo toàn | `.answer-html` có callout, table, code token và diagram classes | Thiết kế reading surface riêng, giữ sanitizer và cấu trúc nội dung |

Không gán điểm số UX hoặc khẳng định contrast/Lighthouse khi chưa đo.

## 4. Những gì không được phá khi redesign

- Session restoration, refresh-token handling và lỗi kết nối; không đổi API/auth contract chỉ để làm demo.
- Tìm kiếm, module/difficulty/tag filters, pagination, debounce và abort/stale-response protections.
- Flashcard quality mapping 0–3, phím tắt 1–4, lịch theo ngày và queue của phiên học.
- Sanitization của answer/blog HTML, các class nội dung được server cho phép.
- Quiz/mock-interview submission, trạng thái loading/error/empty và dữ liệu tiến độ thật.
- Deep links hiện có, quyền truy cập và công việc admin.
- Mọi chỉnh sửa có sẵn của người dùng: repository đang dirty, trong đó `app/dashboard/page.tsx` đã thêm Search Admin trước khảo sát này.

## 5. Cách dùng các skill và Open Design

**Impeccable:** đã load context, init/new-work/document và chạy baseline detect. Cần xác nhận product truth trước khi tạo `PRODUCT.md`; cần chọn visual world trước khi ghi `DESIGN.md`. Audit này không phải design authority.

**Taste-skill (`design-taste-frontend`):** dùng brief inference, anti-default discipline, typography/color consistency và kiểm tra copy. Chính skill giới hạn phạm vi không dành cho dashboard/multi-step product UI, nên không ép các pattern landing page như hero khổng lồ, scroll hijack hay AIDA vào phiên ôn tập. Impeccable Operate/Read sẽ dẫn dắt phần app.

**MCP Open Design:** đã kết nối được và discovery skills thành công. Có `frontend-design`, `design-taste-frontend`, `impeccable-design-polish`. Hiện không có active project. Có thể tạo project riêng theo tên Knowledge Gym để thiết kế mà không phụ thuộc active context; chưa tạo project/run trước khi brief được xác nhận. Prototype cần phản ánh workflow thật; dữ liệu minh họa phải ghi nhãn, không bịa thành tích hoặc tính năng.

## 6. Lộ trình sau xác nhận

1. Xác nhận người học ưu tiên, hành động khi mở app và các ràng buộc phải giữ; viết `PRODUCT.md`.
2. Đề xuất và chọn hướng nhận diện gắn với văn hóa người học; ghi `DESIGN.md` và brief theo từng surface.
3. Tạo prototype bằng Open Design: app shell + điểm bắt đầu học + thư viện/chi tiết câu hỏi; có desktop/mobile.
4. Chuyển hướng được chọn vào code theo nhóm: semantic tokens/shared primitives → auth/shell → questions/reading → practice → progress/support/admin.
5. Kiểm tra chức năng bằng lint/typecheck/tests/build; review trình duyệt desktop/mobile và theme. Một lượt phát hiện theo batch, một lượt sửa, tối đa một lượt xác nhận.
6. Chạy detect trên UI thay đổi, lưu kết quả và ghi rõ các finding còn lại.

## 7. Xác nhận sau khảo sát

Chủ sản phẩm đã xác nhận: phục vụ cả junior và developer đi làm; bắt đầu bằng chọn chủ đề; UI song ngữ Việt/Anh; ưu tiên gam be sáng, không tối thui; redesign bao gồm cả admin. Màu bổ trợ được giao cho người thiết kế.

Product truth đã ghi trong `../../PRODUCT.md`. Chi tiết prototype đề xuất và lỗi authentication Open Design được ghi trong `REDESIGN-BRIEF.md`; chưa có direction hoặc implementation được duyệt.

Chưa sửa mã UI trong lượt khảo sát. Chưa chạy tests/build vì thay đổi hiện tại chỉ là báo cáo và JSON detector; không có kết luận build hoặc test đạt.

## 8. Sau triển khai

- Giao diện Study Commons thay thế identity cũ: canvas be sáng, nền đọc cream, action xanh, selection sage, font Geist local và icon Phosphor.
- `/learn` là điểm bắt đầu, root và đích auth chuyển tới đây; deep links cũ vẫn giữ.
- Shell desktop/mobile, UI Việt/Anh, thư viện/reading, các chế độ luyện, hỗ trợ và cả hai admin đã được cập nhật.
- Open Design không tạo artifact: run đầu lỗi authentication, sau đó daemon không kết nối được; chủ sản phẩm cho phép cách triển khai khả thi khác. Không có artifact hoặc fidelity-to-comp nào được khẳng định.
- Detector cuối: `impeccable-final.json` là `[]`, exit 0.
- Project gates mới nhất đạt: lint không warning/error; TypeScript exit 0; 19 tests/4 files; production build 19 static pages thành công.
- Browser fixture QA mới nhất: 39 checks, 44 captures/axe checks, 0 violations; không có page errors, API fixture thiếu handler hay document overflow. Bốn captures runtime riêng kiểm tra login/mất kết nối không intercept API; ba hostname localhost/IPv4/IPv6 đều HTTP 200. Đây không phải kiểm thử tích hợp backend thật.
- Lượt đầu đã sửa link discrimination, SVG interactive nesting, locality của practice actions, Writer và contrast. Lượt khôi phục mới nhất giữ Profile/ES lifecycle, sửa upload/save/header synchronization, main landmark và network copy; lượt xác nhận mới hoàn tất.
- Workflow reviewer độc lập lỗi trước khi khởi chạy; documenter không chạy. Chủ sản phẩm yêu cầu rà soát/sửa trực tiếp: kết quả tự review được ghi rõ trong FINISH-REVIEW.md và VERDICT.md, không tự gán điểm craft hay nhận independent approval. DESIGN.md và .impeccable/design.json đã được trích xuất từ source thực tế.
- Baseline `npm audit` có 11 vulnerabilities (3 moderate, 5 high, 3 critical). Sau yêu cầu sửa của chủ sản phẩm, Next/React/Vitest và dependencies đã nâng/vá có kiểm chứng: full và production audit đều 0 vulnerabilities, exit 0. Xem SECURITY-REMEDIATION.md; không dùng audit fix --force.
