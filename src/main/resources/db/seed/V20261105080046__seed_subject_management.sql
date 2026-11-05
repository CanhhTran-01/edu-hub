-- Để database tự sinh ID, tránh phải chỉnh lại sequence khi tạo học phần mới qua API.
INSERT INTO subjects (
    subject_code, subject_name, credits, description, status, is_deleted,
    created_at, updated_at, created_by, updated_by
)
SELECT
    seed.subject_code, seed.subject_name, seed.credits, seed.description, seed.status, seed.is_deleted,
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
FROM (VALUES
    ('CS101', 'Nhập môn lập trình', 3, 'Kiến thức cơ bản về lập trình.', 'ACTIVE', FALSE),
    ('CS102', 'Lập trình hướng đối tượng', 3, 'Lớp, đối tượng và các nguyên lý hướng đối tượng.', 'ACTIVE', FALSE),
    ('CS103', 'Cấu trúc dữ liệu và giải thuật', 4, 'Cấu trúc dữ liệu, tìm kiếm và sắp xếp.', 'ACTIVE', FALSE),
    ('CS104', 'Cơ sở dữ liệu', 3, 'Mô hình quan hệ và truy vấn dữ liệu.', 'ACTIVE', FALSE),
    ('CS105', 'Lập trình Web', 3, 'Xây dựng ứng dụng Web cơ bản.', 'ACTIVE', FALSE),
    ('CS106', 'Công nghệ phần mềm', 3, 'Quy trình phát triển và kiểm thử phần mềm.', 'ACTIVE', FALSE),
    ('CS107', 'Hệ điều hành', 3, 'Tiến trình, bộ nhớ và hệ thống tập tin.', 'ACTIVE', FALSE),
    ('CS108', 'Mạng máy tính', 3, 'Các giao thức và mô hình mạng cơ bản.', 'ACTIVE', FALSE),
    ('MATH101', 'Toán rời rạc', 3, 'Logic, tập hợp và lý thuyết đồ thị.', 'ACTIVE', FALSE),
    ('MATH102', 'Xác suất thống kê', 2, 'Xác suất và thống kê cơ bản.', 'ACTIVE', FALSE),
    ('PRJ101', 'Đồ án cơ sở', 5, 'Vận dụng kiến thức để xây dựng một sản phẩm.', 'ACTIVE', FALSE),
    ('SEM101', 'Chuyên đề kỹ năng', 1, 'Học phần độc lập để thử cập nhật, ngừng sử dụng và xóa mềm.', 'ACTIVE', FALSE),
    ('CS090', 'Lập trình Pascal', 3, 'Dữ liệu mẫu học phần đã ngừng sử dụng.', 'INACTIVE', FALSE),
    ('CS099', 'Học phần đã xóa mẫu', 2, 'Không xuất hiện trong API tìm kiếm và xem chi tiết.', 'INACTIVE', TRUE),
    ('CS109', 'Trí tuệ nhân tạo', 3, 'Tìm kiếm, biểu diễn tri thức và các phương pháp suy luận.', 'ACTIVE', FALSE),
    ('CS110', 'Học máy', 4, 'Học có giám sát, học không giám sát và đánh giá mô hình.', 'ACTIVE', FALSE),
    ('CS111', 'An toàn thông tin', 3, 'Mã hóa, xác thực và bảo vệ hệ thống thông tin.', 'ACTIVE', FALSE),
    ('CS112', 'Hệ thống phân tán', 3, 'Giao tiếp, đồng bộ và xử lý dữ liệu trong hệ thống phân tán.', 'ACTIVE', FALSE),
    ('CS113', 'Kiểm thử phần mềm', 3, 'Thiết kế ca kiểm thử, kiểm thử tự động và quản lý lỗi.', 'ACTIVE', FALSE),
    ('CS114', 'Lập trình ứng dụng di động', 3, 'Xây dựng giao diện, quản lý dữ liệu và kết nối API trên thiết bị di động.', 'ACTIVE', FALSE)
) AS seed(subject_code, subject_name, credits, description, status, is_deleted);

-- subject_code là học phần cần học
-- prerequisite_code là môn tiên quyết của nó.
-- Tra ID theo mã để dữ liệu quan hệ không phụ thuộc vào giá trị sequence.
INSERT INTO subject_prerequisites (
    subject_id, prerequisite_subject_id, is_deleted,
    created_at, updated_at, created_by, updated_by
)
SELECT
    subject.id, prerequisite.id, seed.is_deleted,
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
FROM (VALUES
    ('CS102', 'CS101', FALSE),
    ('CS103', 'CS102', FALSE),
    ('CS104', 'CS101', FALSE),
    ('CS105', 'CS102', FALSE),
    ('CS105', 'CS104', FALSE),
    ('CS106', 'CS102', FALSE),
    ('CS107', 'CS103', FALSE),
    ('PRJ101', 'CS103', FALSE),
    ('PRJ101', 'CS104', FALSE),
    ('SEM101', 'CS101', TRUE)
) AS seed(subject_code, prerequisite_code, is_deleted)
JOIN subjects subject ON subject.subject_code = seed.subject_code
JOIN subjects prerequisite ON prerequisite.subject_code = seed.prerequisite_code;
