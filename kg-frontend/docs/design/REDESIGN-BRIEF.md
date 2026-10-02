# Knowledge Gym: brief đã xác nhận và trạng thái prototype

## Xác nhận từ chủ sản phẩm

- Người học: cả junior chuẩn bị phỏng vấn và developer đi làm ôn sâu.
- Điểm bắt đầu: chọn chủ đề, không ưu tiên dashboard hoặc ép phiên flashcard đến hạn.
- Ngôn ngữ UI: Việt/Anh; không tự coi nội dung học hiện tại là đã có bản dịch.
- Nhận diện: thiết kế lại hoàn toàn, thân thiện, sáng tạo nhưng không mang vibe AI; giữ tên Knowledge Gym.
- Màu: chủ sản phẩm thích be sáng, không muốn giao diện tối thui; màu bổ trợ được giao cho người thiết kế.
- Phạm vi: toàn frontend, bao gồm writer/search admin.
- Bảo toàn logic, dữ liệu, API, deep links và mọi thay đổi có sẵn.

Nguồn bền vững: `../../PRODUCT.md`. `DESIGN.md` sẽ mô tả hướng được duyệt/triển khai, chưa ghi một phương án chưa được duyệt thành authority.

## Design read

Ứng dụng luyện kiến thức IT dành cho người mới và người đang đi làm, topic-first, ngôn ngữ hình ảnh be sáng dễ tập trung. Mode chính Operate; phần đọc đáp án/blog là Read. Taste-skill dùng cho chống default và consistency, không áp pattern landing page vào app/admin.

Dials cho prototype: DESIGN_VARIANCE 5, MOTION_INTENSITY 3, VISUAL_DENSITY 5. Các giá trị này không cam kết motion phức tạp.

## Nguồn hướng thiết kế

Cơ chế sản phẩm: chọn kiến thức cần luyện và chọn cách thực hành phù hợp, với đọc/ôn/tự kiểm tra và phản hồi tiến độ cá nhân.

Bối cảnh tham chiếu: văn hóa học và trao đổi kiến thức của developer, không mô phỏng terminal hay cockpit. Loại trừ hai lối mòn: dashboard tối với card số liệu; landing page be + serif + câu chữ quảng cáo.

Danh sách hướng grounded, theo mức phù hợp:
1. Course Index: mục lục syllabus học kỹ thuật, nhóm chủ đề và outline rõ ràng.
2. Peer Review: hệ chữ và dấu nhận xét trong buổi review kiến thức, trọng tâm nội dung và feedback.
3. Reference Library: hệ nhãn phân loại thư viện tài liệu, phân tầng chủ đề/modules.
4. Learning Pathway: nhận diện wayfinding trong khu học tập, màu dẫn đường và lựa chọn rõ ràng.
5. Workshop Handout: tài liệu buổi thực hành, phân cấp task/check/answer.
6. Study Commons: không gian học nhóm và bảng chọn buổi học, rộng rãi, lựa chọn hiện diện rõ.
7. Conference Programme: chương trình chuyên đề và lựa chọn session, mật độ cao nhưng dễ quét.

Impeccable concept-seed đã chạy trong `kg-frontend`, scope direction, mode operate. Key `6bf70492`, index 6: đề xuất Study Commons. Đây là assignment nội bộ, không phải sự duyệt của người dùng. Course Index là alternate đề xuất; mọi phương án giữ ràng buộc be sáng/song ngữ. Các challenger từ seed chưa hoàn tất đánh giá/decision round do hạ tầng Open Design bị chặn; không coi shortlist này là direction đã chốt.

## Hai phương án gửi vào Open Design để xem trước

### Study Commons / Không gian học

- Be yến mạch #F3EEE4, surface #FFFCF6, rail cát nhạt #E8DFCD, ink #273C4A, CTA xanh trầm #345F73, selection sage #DCE7D9.
- Sans dễ đọc; rail trái ổn định, admin là khu riêng.
- First viewport: “Bạn muốn học gì hôm nay?”, tìm kiếm, vùng chọn chủ đề/module chính, vùng mode xuất hiện theo module được chọn.
- Signature: selection tab nhỏ màu sage làm rõ module và mở các cách luyện ngay cạnh, không nested cards.
- Rủi ro: rail nhiều mục có thể quá dày trên mobile; cần ưu tiên điều hướng và menu có trạng thái rõ.

### Course Index / Mục lục học tập

- Be sáng, ink #33412E, action olive #52633F.
- Top navigation, không permanent app rail; cột chủ đề nhỏ và outline/module lớn, thiên về typography.
- Signature: outline của chủ đề được chọn mở inline và sử dụng được bằng keyboard.
- Rủi ro: gần giao diện thư viện học quen thuộc; cần thực thi typography/spacing tốt để không thành danh sách vô hồn.

Đây là đề xuất, chưa được chủ sản phẩm chọn. Palette là candidate, chưa phải token sản xuất.

## Prototype được yêu cầu

Một artifact review có hai hướng thực sự khác về tổ chức giao diện, không chỉ đổi màu. Mỗi hướng có:
- Topic selector/search và các thao tác chọn module/practice.
- Reading view với câu hỏi mẫu “Phân biệt JDK, JRE và JVM?” và đáp án chính xác.
- Search Admin với control rõ, confirmation trước thao tác destructive mô phỏng, error/retry.
- Writer Admin với draft/preview/publish mô phỏng.
- Toggle Việt/Anh lưu lựa chọn, menu mobile, keyboard focus.
- Nhãn dữ liệu minh họa và thông báo prototype không nối API.

Không bịa achievements, không network production, không sửa repo application. Kiểm tra desktop/mobile theo batch và tối đa một lượt sửa/xác nhận.

## Open Design: hạ tầng bị chặn

- Project: `knowledge-gym-bright-redesign`.
- Project directory: `/Users/bao2k1/Library/Application Support/Open Design/namespaces/release-stable/data/projects/knowledge-gym-bright-redesign`.
- Run: `48b1f24f-c7d1-4fdc-8cbd-4267800d980f`.
- Agent mặc định daemon chọn: `cursor-agent`.
- Status: `failed`; exit code `1`; `resumable: false`; `artifactCount: 0`.
- Error code: `AGENT_AUTH_REQUIRED`, category `auth`.
- Lỗi nguyên văn:

> Cursor Agent is not authenticated. Run `cursor-agent login`, then `cursor-agent status`, and retry. For automation, ensure CURSOR_API_KEY is set in the Open Design process environment.

Response còn chứa hint “Run still in flight”, nhưng status terminal failed/exit code 1 có ưu tiên; không polling mãi theo hint trái trạng thái.

Không có preview URL hoặc artifact để đánh giá. Không tự đổi agent hay viết artifact thay Open Design sau lỗi. Chờ chủ sản phẩm xử lý authentication hoặc cho phép chọn agent khác rõ ràng. Không thu thập/ghi API key vào brief.

## Bước kế tiếp khi được mở chặn

1. Retry cùng brief qua MCP Open Design sau khi authentication hợp lệ, hoặc dùng agent được chủ sản phẩm cho phép.
2. Nhận artifact/preview và kiểm tra bằng evidence thực tế.
3. Hoàn tất direction decision/duyệt trước khi đổi mã frontend.
4. Implement semantic tokens, UI localization, shared shell, topic-first entry, reading/practice/progress/support và admin theo nhóm có kiểm chứng.
5. Lint/typecheck/tests/build; browser desktop/mobile; detect UI thay đổi; document DESIGN.md từ hệ thực tế.

## Kiểm chứng ở lượt này

Đã tạo PRODUCT.md và brief; không sửa UI. Không chạy lint/tests/build vì chưa có implementation thay đổi. Application workspace có nhiều thay đổi backend/frontend sẵn có, không reset/restore hoặc sửa chúng. Open Design run dùng workspace od-owned riêng và thất bại trước tạo artifact.
