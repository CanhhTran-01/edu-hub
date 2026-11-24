-- Tệp TXT gốc và hash SHA-256 là dữ liệu thật của tài liệu demo, không tạo vector giả hoặc gán ACTIVE giả.
-- Ba nguồn FAILED có thể chọn xử lý lại qua API để worker tạo chỉ mục đúng model/version đang cấu hình.
-- NOTE: Cần cấu hình GOOGLE_API_KEY trước khi xử lý lại; seed không tự gọi dịch vụ AI hoặc gửi tài liệu ra ngoài.
INSERT INTO knowledge_sources (uploaded_by, title, document_type, original_filename, original_file, file_hash,
                               uploaded_at, effective_date, expiry_date, status, error_message, is_deleted,
                               created_at, updated_at, created_by, updated_by)
SELECT account.id, document.title, 'HUONG_DAN_DEMO', document.original_filename,
       CASE WHEN document.status = 'DELETED' THEN NULL ELSE convert_to(document.content, 'UTF8') END,
       encode(sha256(convert_to(document.content, 'UTF8')), 'hex'),
       document.effective_date::TIMESTAMP + INTERVAL '9 hours', document.effective_date,
       CASE WHEN document.status IN ('INACTIVE','DELETED') THEN DATE '2025-12-31' END,
       document.status,
       CASE WHEN document.status = 'FAILED' THEN 'Nguồn demo chưa có chỉ mục. Chọn xử lý lại để tạo embedding theo cấu hình local.' END,
       document.status = 'DELETED', document.effective_date::TIMESTAMP + INTERVAL '2 hours',
       TIMESTAMP '2026-11-23 02:00:00', 'demo-seed', 'demo-seed'
FROM (VALUES
    ('huong-dan-dang-ky-demo-2026.txt', 'DEMO 2026 - Hướng dẫn đăng ký và hủy học phần', 'FAILED', DATE '2026-08-01', 'TÀI LIỆU DEMO LOCAL EDUHUB, KHÔNG PHẢI QUY CHẾ CHÍNH THỨC.
Đăng ký học phần: sinh viên đăng nhập bằng tài khoản cá nhân, chọn kỳ đang mở đăng ký và lớp học phần còn chỗ. Chỉ đăng ký học phần thuộc chương trình đào tạo của mình, đã đạt các môn tiên quyết và không trùng lịch với lớp đang học.
Trong bộ demo, giới hạn tín chỉ là 12 đến 24 tín chỉ mỗi kỳ. Dưới 12 tín chỉ vẫn được lưu và hiển thị cảnh báo; vượt 24 tín chỉ bị từ chối.
Hủy học phần chỉ thực hiện trong khoảng thời gian đăng ký của kỳ. Hủy giữ lại lịch sử, không xóa lượt học. Sau khi hủy có thể đăng ký lại nếu còn đủ điều kiện.'),
    ('huong-dan-diem-demo-2026.txt', 'DEMO 2026 - Điểm quá trình, điểm thi và khóa điểm', 'FAILED', DATE '2026-08-01', 'TÀI LIỆU DEMO LOCAL EDUHUB, KHÔNG PHẢI QUY CHẾ CHÍNH THỨC.
Điểm quá trình bằng 30% TP1 cộng 70% TP2, làm tròn một chữ số thập phân. Chưa đủ hai thành phần thì chưa có điểm quá trình. Điểm 0 là đã nhập, khác với ô trống.
Điểm tổng kết bằng 30% điểm quá trình cộng 70% điểm thi học phần, làm tròn một chữ số thập phân. Trong bộ demo, từ 4 điểm là đạt; dưới 4 điểm là không đạt.
Chỉ giảng viên được phân công nhập điểm quá trình cho lớp đã chốt danh sách. Admin học vụ nhập điểm thi khi đã có điểm quá trình. Lần công bố đầu tiên chốt hạn điều chỉnh theo cấu hình, mặc định demo là 14 ngày. Điều chỉnh không gia hạn mốc khóa. Sinh viên chỉ xem kết quả của chính mình.'),
    ('huong-dan-ho-so-ctdt-demo-2026.txt', 'DEMO 2026 - Hồ sơ, chương trình đào tạo và học lại', 'FAILED', DATE '2026-08-01', 'TÀI LIỆU DEMO LOCAL EDUHUB, KHÔNG PHẢI QUY CHẾ CHÍNH THỨC.
Sinh viên xem chương trình đào tạo theo ngành, khóa và hệ đào tạo. Mỗi chương trình chính quy trong bộ demo có 8 kỳ lộ trình. Kỳ lộ trình khác ID của kỳ học thực tế.
Sinh viên được cập nhật số điện thoại, email và GPA mục tiêu trên hồ sơ cá nhân. Thay email tài khoản không sửa email snapshot trong lịch sử nhập học. Mã sinh viên và tài khoản đã cấp được giữ nguyên.
Môn đã không đạt có thể đăng ký học lại khi đủ tiên quyết và có lớp mở; mỗi lần học là một lượt riêng. Điểm và tín chỉ của lượt cũ được giữ lại. Môn đã đạt chưa có luồng học cải thiện trong phiên bản này. GPA tích lũy dùng kết quả mới nhất của mỗi môn có trọng số tín chỉ.'),
    ('huong-dan-luu-tru-demo-2025.txt', 'DEMO 2025 - Hướng dẫn đã lưu trữ', 'INACTIVE', DATE '2025-08-01', 'TÀI LIỆU DEMO ĐÃ LƯU TRỮ. Nội dung phiên bản cũ chỉ giữ để minh họa lịch sử trích dẫn. Không dùng tài liệu này để tư vấn hoặc xác định quy chế đang áp dụng.'),
    ('huong-dan-da-go-demo-2024.txt', 'DEMO 2024 - Tài liệu đã gỡ', 'DELETED', DATE '2024-08-01', 'TÀI LIỆU DEMO ĐÃ GỠ. Nội dung và vector đã được xóa; giữ định danh nguồn cho lịch sử hội thoại.')
) AS document(original_filename, title, status, effective_date, content)
CROSS JOIN users account WHERE account.username = 'academic_admin';

-- Chunk lưu trữ giữ nội dung/vị trí cho lịch sử; không có vector tương thích và không tham gia truy xuất.
INSERT INTO knowledge_chunks (source_id, chunk_index, content, token_count, section_title, is_deleted,
                              created_at, updated_at, created_by, updated_by)
SELECT source.id, chunk_position - 1,
       CASE WHEN source.status = 'DELETED' THEN 'Nội dung nguồn đã được gỡ.'
            ELSE 'Nội dung demo lưu trữ, mục ' || chunk_position || '. Chỉ phục vụ minh họa trích dẫn cũ.' END,
       30, 'Mục ' || chunk_position, source.is_deleted,
       source.created_at, source.updated_at, 'demo-seed', 'demo-seed'
FROM knowledge_sources source CROSS JOIN generate_series(1,3) AS chunk_position
WHERE source.status IN ('INACTIVE','DELETED');

CREATE TEMP TABLE demo_chat_sessions ON COMMIT DROP AS
SELECT account.id AS user_id, conversation_position,
       TIMESTAMP '2026-11-20 09:00:00' + (conversation_position - 1) * INTERVAL '1 day'
           + (account.id % 20) * INTERVAL '1 minute' AS started_at,
       CASE conversation_position WHEN 1 THEN 'Điều kiện đăng ký học phần trong kỳ'
                                  WHEN 2 THEN 'Cách xem điểm và học lại'
                                  ELSE 'Câu hỏi ngoài phạm vi đào tạo' END AS title
FROM users account JOIN students student ON student.user_id = account.id
CROSS JOIN generate_series(1,3) AS conversation_position
WHERE NOT account.is_deleted AND (account.username = 'student' OR (student.id % 83 = 0 AND student.activity_status = 'STUDYING'));

INSERT INTO chat_sessions (user_id, title, started_at, last_message_at, topic_type,
                           created_at, updated_at, created_by, updated_by)
SELECT user_id, title, started_at, started_at + INTERVAL '2 minutes', 'KNOWLEDGE',
       started_at - INTERVAL '7 hours', started_at - INTERVAL '7 hours' + INTERVAL '2 minutes', 'demo-seed', 'demo-seed'
FROM demo_chat_sessions ORDER BY user_id, conversation_position;

INSERT INTO chat_messages (session_id, role, content, intent, sent_at, created_at, updated_at, created_by, updated_by)
SELECT session.id, 'USER',
       CASE WHEN session.title LIKE 'Điều kiện%' THEN 'Tôi cần đáp ứng điều kiện nào khi đăng ký học phần?'
            WHEN session.title LIKE 'Cách xem%' THEN 'Môn chưa đạt có được học lại không?'
            ELSE 'Bạn có thể dự đoán kết quả bóng đá giúp tôi không?' END,
       CASE WHEN session.title LIKE 'Câu hỏi%' THEN 'OUT_OF_SCOPE' ELSE 'KNOWLEDGE' END,
       session.started_at, session.created_at, session.created_at, 'demo-seed', 'demo-seed'
FROM chat_sessions session ORDER BY session.id;

INSERT INTO chat_messages (session_id, role, content, intent, sent_at, created_at, updated_at, created_by, updated_by)
SELECT session.id, 'ASSISTANT',
       CASE WHEN session.title LIKE 'Câu hỏi%' THEN 'Tôi hỗ trợ quy chế và thủ tục đào tạo. Bạn hãy đặt câu hỏi về học vụ.'
            ELSE 'Đây là câu trả lời demo được lưu từ tài liệu cũ [K' || chunk.id || ']. Nguồn hiện không còn khả dụng; vui lòng tra tài liệu đang có hiệu lực hoặc liên hệ phòng đào tạo.' END,
       CASE WHEN session.title LIKE 'Câu hỏi%' THEN 'OUT_OF_SCOPE' ELSE 'KNOWLEDGE' END,
       session.started_at + INTERVAL '2 minutes', session.created_at + INTERVAL '2 minutes',
       session.created_at + INTERVAL '2 minutes', 'demo-seed', 'demo-seed'
FROM chat_sessions session CROSS JOIN LATERAL (
    SELECT knowledge_chunk.id FROM knowledge_chunks knowledge_chunk JOIN knowledge_sources source ON source.id = knowledge_chunk.source_id
    WHERE source.status = CASE WHEN session.title LIKE 'Điều kiện%' THEN 'INACTIVE' ELSE 'DELETED' END
    ORDER BY knowledge_chunk.id LIMIT 1
) chunk ORDER BY session.id;

INSERT INTO message_chunk_references (message_id, chunk_id, relevance_score, created_at, updated_at, created_by, updated_by)
SELECT message.id, chunk.id, 0.91, message.created_at, message.created_at, 'demo-seed', 'demo-seed'
FROM chat_messages message JOIN chat_sessions session ON session.id = message.session_id
CROSS JOIN LATERAL (
    SELECT knowledge_chunk.id FROM knowledge_chunks knowledge_chunk JOIN knowledge_sources source ON source.id = knowledge_chunk.source_id
    WHERE source.status = CASE WHEN session.title LIKE 'Điều kiện%' THEN 'INACTIVE' ELSE 'DELETED' END
    ORDER BY knowledge_chunk.id LIMIT 1
) chunk
WHERE message.role = 'ASSISTANT' AND message.intent = 'KNOWLEDGE';

-- audit_logs vẫn rỗng vì ứng dụng chưa triển khai ghi audit; không giả lập hành vi chưa có.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM knowledge_sources WHERE status = 'ACTIVE')
       OR EXISTS (SELECT 1 FROM knowledge_chunks WHERE embedding IS NOT NULL)
    THEN RAISE EXCEPTION 'Seed không được giả lập chỉ mục AI đã xử lý thành công'; END IF;
    IF EXISTS (SELECT 1 FROM chat_messages WHERE sent_at > TIMESTAMP '2026-11-24 23:59:59')
    THEN RAISE EXCEPTION 'Lịch sử hội thoại vượt mốc demo'; END IF;
END $$;
