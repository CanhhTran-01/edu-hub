-- Dữ liệu dev/test: 4 ngành, 4 CTĐT chính quy 4 năm, 40 môn bắt buộc/CTĐT.
-- Từng học phần và kỳ khuyến nghị được liệt kê tường minh trong VALUES.
-- ID do database sinh; tra khóa ngoại theo mã để không phụ thuộc sequence.

INSERT INTO majors (
    major_code, major_name, status, created_at, updated_at, created_by, updated_by, is_deleted
)
SELECT seed.major_code, seed.major_name, 'ACTIVE',
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed', FALSE
FROM (VALUES
    ('CNTT', 'Công nghệ thông tin'),
    ('ATTT', 'An toàn thông tin'),
    ('DTVT', 'Điện tử viễn thông'),
    ('TTNT', 'Trí tuệ nhân tạo')
) AS seed(major_code, major_name);

-- Bổ sung 104 học phần; dùng lại các mã CS/MATH/PRJ đã có trong migration học phần.
INSERT INTO subjects (
    subject_code, subject_name, credits, status, created_at, updated_at, created_by, updated_by, is_deleted
)
SELECT seed.subject_code, seed.subject_name, seed.credits, 'ACTIVE',
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed', FALSE
FROM (VALUES
    -- Học phần nền tảng dùng chung
    ('MATH103', 'Giải tích 1', 3),
    ('MATH104', 'Đại số tuyến tính', 3),
    ('GEN101', 'Tiếng Anh cơ bản', 3),
    ('GEN102', 'Kỹ năng học tập đại học', 2),
    ('GEN103', 'Tiếng Anh chuyên ngành', 3),
    -- Học phần Công nghệ thông tin
    ('IT101', 'Kiến trúc máy tính', 3),
    ('IT201', 'Nguyên lý hệ quản trị cơ sở dữ liệu', 3),
    ('IT202', 'Thiết kế giao diện người dùng', 3),
    ('IT203', 'Lập trình Java', 3),
    ('IT204', 'Quản trị hệ thống Linux', 3),
    ('IT301', 'Phân tích và thiết kế hệ thống thông tin', 3),
    ('IT302', 'Lập trình mạng', 3),
    ('IT303', 'Phát triển dịch vụ Web', 3),
    ('IT304', 'Điện toán đám mây', 3),
    ('IT401', 'Kiến trúc phần mềm', 3),
    ('IT402', 'DevOps', 3),
    ('IT403', 'Quản lý dự án phần mềm', 3),
    ('IT404', 'Phân tích dữ liệu doanh nghiệp', 3),
    ('IT405', 'Thực tập doanh nghiệp Công nghệ thông tin', 4),
    ('IT406', 'Tích hợp hệ thống', 3),
    ('IT407', 'Tối ưu hiệu năng ứng dụng', 3),
    ('IT408', 'Phát triển ứng dụng doanh nghiệp', 3),
    ('IT409', 'Chuyên đề Công nghệ thông tin', 3),
    ('IT410', 'Khóa luận tốt nghiệp Công nghệ thông tin', 5),
    -- Học phần An toàn thông tin
    ('SEC101', 'Nhập môn an ninh mạng', 3),
    ('SEC201', 'Toán học cho mật mã', 3),
    ('SEC202', 'Mật mã học', 3),
    ('SEC203', 'Bảo mật hệ điều hành', 3),
    ('SEC204', 'An toàn mạng máy tính', 3),
    ('SEC205', 'Lập trình an toàn', 3),
    ('SEC301', 'An toàn ứng dụng Web', 3),
    ('SEC302', 'Quản trị an ninh hệ thống', 3),
    ('SEC303', 'Kiểm thử xâm nhập', 3),
    ('SEC304', 'Phân tích mã độc', 3),
    ('SEC305', 'Đồ án An toàn thông tin', 4),
    ('SEC306', 'Điều tra số', 3),
    ('SEC307', 'Phát hiện và ngăn chặn xâm nhập', 3),
    ('SEC308', 'An toàn cơ sở dữ liệu', 3),
    ('SEC309', 'Quản lý rủi ro an toàn thông tin', 3),
    ('SEC310', 'An toàn điện toán đám mây', 3),
    ('SEC401', 'Giám sát và vận hành SOC', 3),
    ('SEC402', 'Ứng cứu sự cố an ninh mạng', 3),
    ('SEC403', 'Bảo mật thiết bị IoT', 3),
    ('SEC404', 'Phân tích tình báo mối đe dọa', 3),
    ('SEC405', 'Thực tập doanh nghiệp An toàn thông tin', 4),
    ('SEC406', 'Kiểm toán an toàn thông tin', 3),
    ('SEC407', 'Pháp luật và đạo đức an toàn thông tin', 3),
    ('SEC408', 'Thiết kế hệ thống bảo mật', 3),
    ('SEC409', 'Chuyên đề An toàn thông tin', 3),
    ('SEC410', 'Khóa luận tốt nghiệp An toàn thông tin', 5),
    -- Học phần Điện tử viễn thông
    ('ET101', 'Vật lý đại cương', 3),
    ('ET201', 'Lý thuyết mạch', 3),
    ('ET202', 'Linh kiện điện tử', 3),
    ('ET203', 'Tín hiệu và hệ thống', 3),
    ('ET204', 'Kỹ thuật điện tử tương tự', 3),
    ('ET205', 'Kỹ thuật số', 3),
    ('ET206', 'Trường điện từ', 3),
    ('ET207', 'Đo lường điện tử', 3),
    ('ET301', 'Xử lý tín hiệu số', 3),
    ('ET302', 'Thông tin số', 3),
    ('ET303', 'Vi xử lý và vi điều khiển', 3),
    ('ET304', 'Kỹ thuật truyền dẫn', 3),
    ('ET305', 'Đồ án Điện tử viễn thông', 4),
    ('ET306', 'Mạng viễn thông', 3),
    ('ET307', 'Thông tin di động', 3),
    ('ET308', 'Thông tin quang', 3),
    ('ET309', 'Anten và truyền sóng', 3),
    ('ET310', 'Thiết kế hệ thống nhúng', 3),
    ('ET401', 'Mạng cảm biến không dây', 3),
    ('ET402', 'Thiết kế mạch tích hợp', 3),
    ('ET403', 'Hệ thống truyền thông IoT', 3),
    ('ET404', 'Kỹ thuật siêu cao tần', 3),
    ('ET405', 'Thực tập doanh nghiệp Điện tử viễn thông', 4),
    ('ET406', 'Mạng viễn thông thế hệ mới', 3),
    ('ET407', 'Thiết kế hệ thống viễn thông', 3),
    ('ET408', 'Xử lý ảnh trong hệ thống nhúng', 3),
    ('ET409', 'Chuyên đề Điện tử viễn thông', 3),
    ('ET410', 'Khóa luận tốt nghiệp Điện tử viễn thông', 5),
    -- Học phần Trí tuệ nhân tạo
    ('MATH105', 'Giải tích 2', 3),
    ('AI201', 'Tối ưu hóa cho học máy', 3),
    ('AI202', 'Lập trình Python cho khoa học dữ liệu', 3),
    ('AI203', 'Phân tích dữ liệu khám phá', 3),
    ('AI204', 'Toán học cho học sâu', 3),
    ('AI205', 'Biểu diễn tri thức', 3),
    ('AI301', 'Học sâu', 3),
    ('AI302', 'Xử lý ngôn ngữ tự nhiên', 3),
    ('AI303', 'Thị giác máy tính', 3),
    ('AI304', 'Khai phá dữ liệu', 3),
    ('AI305', 'Đồ án Trí tuệ nhân tạo', 4),
    ('AI306', 'Học tăng cường', 3),
    ('AI307', 'Dữ liệu lớn', 3),
    ('AI308', 'Hệ gợi ý', 3),
    ('AI309', 'Mô hình sinh', 3),
    ('AI310', 'Đánh giá mô hình trí tuệ nhân tạo', 3),
    ('AI401', 'Triển khai mô hình học máy', 3),
    ('AI402', 'MLOps', 3),
    ('AI403', 'Trí tuệ nhân tạo có trách nhiệm', 3),
    ('AI404', 'Kỹ thuật dữ liệu', 3),
    ('AI405', 'Thực tập doanh nghiệp Trí tuệ nhân tạo', 4),
    ('AI406', 'Tối ưu hóa mô hình học sâu', 3),
    ('AI407', 'Trí tuệ nhân tạo trên thiết bị biên', 3),
    ('AI408', 'Ứng dụng mô hình ngôn ngữ lớn', 3),
    ('AI409', 'Chuyên đề Trí tuệ nhân tạo', 3),
    ('AI410', 'Khóa luận tốt nghiệp Trí tuệ nhân tạo', 5)
) AS seed(subject_code, subject_name, credits);

-- Chỉ là mẫu CTĐT; mapping với khóa tuyển sinh do BE1/People cung cấp sau.
-- Tra ID bằng scalar subquery: mã tham chiếu thiếu sẽ vi phạm NOT NULL thay vì mất dòng seed.
INSERT INTO curriculums (
    curriculum_code, curriculum_name, major_id, training_type, duration_semesters, status,
    created_at, updated_at, created_by, updated_by, is_deleted
)
SELECT seed.curriculum_code, seed.curriculum_name,
    (SELECT id FROM majors WHERE major_code = seed.major_code AND status = 'ACTIVE' AND is_deleted = FALSE),
    seed.training_type, seed.duration_semesters, 'ACTIVE',
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed', FALSE
FROM (VALUES
    ('CNTT_4Y_V1', 'Chương trình đào tạo Công nghệ thông tin 4 năm', 'CNTT', 'CHINH_QUY', 8),
    ('ATTT_4Y_V1', 'Chương trình đào tạo An toàn thông tin 4 năm', 'ATTT', 'CHINH_QUY', 8),
    ('DTVT_4Y_V1', 'Chương trình đào tạo Điện tử viễn thông 4 năm', 'DTVT', 'CHINH_QUY', 8),
    ('TTNT_4Y_V1', 'Chương trình đào tạo Trí tuệ nhân tạo 4 năm', 'TTNT', 'CHINH_QUY', 8)
) AS seed(curriculum_code, curriculum_name, major_code, training_type, duration_semesters);

-- 160 quan hệ: 4 CTĐT x 8 kỳ x 5 môn, tất cả REQUIRED.
-- recommend_semester thuộc quan hệ CTĐT - học phần, không gắn vào Subject.
INSERT INTO curriculum_subjects (
    curriculum_id, subject_id, recommend_semester, subject_type,
    created_at, updated_at, created_by, updated_by, is_deleted
)
SELECT
    (SELECT id FROM curriculums
     WHERE curriculum_code = seed.curriculum_code AND status = 'ACTIVE' AND is_deleted = FALSE),
    (SELECT id FROM subjects
     WHERE subject_code = seed.subject_code AND status = 'ACTIVE' AND is_deleted = FALSE),
    seed.recommend_semester, 'REQUIRED',
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed', FALSE
FROM (VALUES
    -- Công nghệ thông tin: 40 môn, 124 tín chỉ
    -- Kỳ 1
    ('CNTT_4Y_V1', 'MATH103', 1),
    ('CNTT_4Y_V1', 'MATH104', 1),
    ('CNTT_4Y_V1', 'CS101', 1),
    ('CNTT_4Y_V1', 'GEN101', 1),
    ('CNTT_4Y_V1', 'GEN102', 1),
    -- Kỳ 2
    ('CNTT_4Y_V1', 'MATH101', 2),
    ('CNTT_4Y_V1', 'MATH102', 2),
    ('CNTT_4Y_V1', 'CS102', 2),
    ('CNTT_4Y_V1', 'GEN103', 2),
    ('CNTT_4Y_V1', 'IT101', 2),
    -- Kỳ 3
    ('CNTT_4Y_V1', 'CS103', 3),
    ('CNTT_4Y_V1', 'CS104', 3),
    ('CNTT_4Y_V1', 'CS108', 3),
    ('CNTT_4Y_V1', 'IT202', 3),
    ('CNTT_4Y_V1', 'IT201', 3),
    -- Kỳ 4
    ('CNTT_4Y_V1', 'CS105', 4),
    ('CNTT_4Y_V1', 'CS106', 4),
    ('CNTT_4Y_V1', 'CS107', 4),
    ('CNTT_4Y_V1', 'IT203', 4),
    ('CNTT_4Y_V1', 'IT204', 4),
    -- Kỳ 5
    ('CNTT_4Y_V1', 'CS109', 5),
    ('CNTT_4Y_V1', 'CS111', 5),
    ('CNTT_4Y_V1', 'IT301', 5),
    ('CNTT_4Y_V1', 'IT302', 5),
    ('CNTT_4Y_V1', 'PRJ101', 5),
    -- Kỳ 6
    ('CNTT_4Y_V1', 'CS112', 6),
    ('CNTT_4Y_V1', 'CS113', 6),
    ('CNTT_4Y_V1', 'CS114', 6),
    ('CNTT_4Y_V1', 'IT303', 6),
    ('CNTT_4Y_V1', 'IT304', 6),
    -- Kỳ 7
    ('CNTT_4Y_V1', 'IT401', 7),
    ('CNTT_4Y_V1', 'IT402', 7),
    ('CNTT_4Y_V1', 'IT403', 7),
    ('CNTT_4Y_V1', 'IT404', 7),
    ('CNTT_4Y_V1', 'IT405', 7),
    -- Kỳ 8
    ('CNTT_4Y_V1', 'IT406', 8),
    ('CNTT_4Y_V1', 'IT407', 8),
    ('CNTT_4Y_V1', 'IT408', 8),
    ('CNTT_4Y_V1', 'IT409', 8),
    ('CNTT_4Y_V1', 'IT410', 8),
    -- An toàn thông tin: 40 môn, 123 tín chỉ
    -- Kỳ 1
    ('ATTT_4Y_V1', 'MATH103', 1),
    ('ATTT_4Y_V1', 'MATH104', 1),
    ('ATTT_4Y_V1', 'CS101', 1),
    ('ATTT_4Y_V1', 'GEN101', 1),
    ('ATTT_4Y_V1', 'GEN102', 1),
    -- Kỳ 2
    ('ATTT_4Y_V1', 'MATH101', 2),
    ('ATTT_4Y_V1', 'MATH102', 2),
    ('ATTT_4Y_V1', 'CS102', 2),
    ('ATTT_4Y_V1', 'GEN103', 2),
    ('ATTT_4Y_V1', 'SEC101', 2),
    -- Kỳ 3
    ('ATTT_4Y_V1', 'CS103', 3),
    ('ATTT_4Y_V1', 'CS104', 3),
    ('ATTT_4Y_V1', 'CS108', 3),
    ('ATTT_4Y_V1', 'CS111', 3),
    ('ATTT_4Y_V1', 'SEC201', 3),
    -- Kỳ 4
    ('ATTT_4Y_V1', 'CS107', 4),
    ('ATTT_4Y_V1', 'SEC202', 4),
    ('ATTT_4Y_V1', 'SEC203', 4),
    ('ATTT_4Y_V1', 'SEC204', 4),
    ('ATTT_4Y_V1', 'SEC205', 4),
    -- Kỳ 5
    ('ATTT_4Y_V1', 'SEC301', 5),
    ('ATTT_4Y_V1', 'SEC302', 5),
    ('ATTT_4Y_V1', 'SEC303', 5),
    ('ATTT_4Y_V1', 'SEC304', 5),
    ('ATTT_4Y_V1', 'SEC305', 5),
    -- Kỳ 6
    ('ATTT_4Y_V1', 'SEC306', 6),
    ('ATTT_4Y_V1', 'SEC307', 6),
    ('ATTT_4Y_V1', 'SEC308', 6),
    ('ATTT_4Y_V1', 'SEC309', 6),
    ('ATTT_4Y_V1', 'SEC310', 6),
    -- Kỳ 7
    ('ATTT_4Y_V1', 'SEC401', 7),
    ('ATTT_4Y_V1', 'SEC402', 7),
    ('ATTT_4Y_V1', 'SEC403', 7),
    ('ATTT_4Y_V1', 'SEC404', 7),
    ('ATTT_4Y_V1', 'SEC405', 7),
    -- Kỳ 8
    ('ATTT_4Y_V1', 'SEC406', 8),
    ('ATTT_4Y_V1', 'SEC407', 8),
    ('ATTT_4Y_V1', 'SEC408', 8),
    ('ATTT_4Y_V1', 'SEC409', 8),
    ('ATTT_4Y_V1', 'SEC410', 8),
    -- Điện tử viễn thông: 40 môn, 123 tín chỉ
    -- Kỳ 1
    ('DTVT_4Y_V1', 'MATH103', 1),
    ('DTVT_4Y_V1', 'MATH104', 1),
    ('DTVT_4Y_V1', 'CS101', 1),
    ('DTVT_4Y_V1', 'GEN101', 1),
    ('DTVT_4Y_V1', 'GEN102', 1),
    -- Kỳ 2
    ('DTVT_4Y_V1', 'MATH101', 2),
    ('DTVT_4Y_V1', 'MATH102', 2),
    ('DTVT_4Y_V1', 'CS102', 2),
    ('DTVT_4Y_V1', 'GEN103', 2),
    ('DTVT_4Y_V1', 'ET101', 2),
    -- Kỳ 3
    ('DTVT_4Y_V1', 'CS103', 3),
    ('DTVT_4Y_V1', 'CS104', 3),
    ('DTVT_4Y_V1', 'CS108', 3),
    ('DTVT_4Y_V1', 'ET201', 3),
    ('DTVT_4Y_V1', 'ET202', 3),
    -- Kỳ 4
    ('DTVT_4Y_V1', 'ET203', 4),
    ('DTVT_4Y_V1', 'ET204', 4),
    ('DTVT_4Y_V1', 'ET205', 4),
    ('DTVT_4Y_V1', 'ET206', 4),
    ('DTVT_4Y_V1', 'ET207', 4),
    -- Kỳ 5
    ('DTVT_4Y_V1', 'ET301', 5),
    ('DTVT_4Y_V1', 'ET302', 5),
    ('DTVT_4Y_V1', 'ET303', 5),
    ('DTVT_4Y_V1', 'ET304', 5),
    ('DTVT_4Y_V1', 'ET305', 5),
    -- Kỳ 6
    ('DTVT_4Y_V1', 'ET306', 6),
    ('DTVT_4Y_V1', 'ET307', 6),
    ('DTVT_4Y_V1', 'ET308', 6),
    ('DTVT_4Y_V1', 'ET309', 6),
    ('DTVT_4Y_V1', 'ET310', 6),
    -- Kỳ 7
    ('DTVT_4Y_V1', 'ET401', 7),
    ('DTVT_4Y_V1', 'ET402', 7),
    ('DTVT_4Y_V1', 'ET403', 7),
    ('DTVT_4Y_V1', 'ET404', 7),
    ('DTVT_4Y_V1', 'ET405', 7),
    -- Kỳ 8
    ('DTVT_4Y_V1', 'ET406', 8),
    ('DTVT_4Y_V1', 'ET407', 8),
    ('DTVT_4Y_V1', 'ET408', 8),
    ('DTVT_4Y_V1', 'ET409', 8),
    ('DTVT_4Y_V1', 'ET410', 8),
    -- Trí tuệ nhân tạo: 40 môn, 124 tín chỉ
    -- Kỳ 1
    ('TTNT_4Y_V1', 'MATH103', 1),
    ('TTNT_4Y_V1', 'MATH104', 1),
    ('TTNT_4Y_V1', 'CS101', 1),
    ('TTNT_4Y_V1', 'GEN101', 1),
    ('TTNT_4Y_V1', 'GEN102', 1),
    -- Kỳ 2
    ('TTNT_4Y_V1', 'MATH101', 2),
    ('TTNT_4Y_V1', 'MATH102', 2),
    ('TTNT_4Y_V1', 'CS102', 2),
    ('TTNT_4Y_V1', 'GEN103', 2),
    ('TTNT_4Y_V1', 'MATH105', 2),
    -- Kỳ 3
    ('TTNT_4Y_V1', 'CS103', 3),
    ('TTNT_4Y_V1', 'CS104', 3),
    ('TTNT_4Y_V1', 'CS108', 3),
    ('TTNT_4Y_V1', 'AI201', 3),
    ('TTNT_4Y_V1', 'AI202', 3),
    -- Kỳ 4
    ('TTNT_4Y_V1', 'CS109', 4),
    ('TTNT_4Y_V1', 'CS110', 4),
    ('TTNT_4Y_V1', 'AI203', 4),
    ('TTNT_4Y_V1', 'AI204', 4),
    ('TTNT_4Y_V1', 'AI205', 4),
    -- Kỳ 5
    ('TTNT_4Y_V1', 'AI301', 5),
    ('TTNT_4Y_V1', 'AI302', 5),
    ('TTNT_4Y_V1', 'AI303', 5),
    ('TTNT_4Y_V1', 'AI304', 5),
    ('TTNT_4Y_V1', 'AI305', 5),
    -- Kỳ 6
    ('TTNT_4Y_V1', 'AI306', 6),
    ('TTNT_4Y_V1', 'AI307', 6),
    ('TTNT_4Y_V1', 'AI308', 6),
    ('TTNT_4Y_V1', 'AI309', 6),
    ('TTNT_4Y_V1', 'AI310', 6),
    -- Kỳ 7
    ('TTNT_4Y_V1', 'AI401', 7),
    ('TTNT_4Y_V1', 'AI402', 7),
    ('TTNT_4Y_V1', 'AI403', 7),
    ('TTNT_4Y_V1', 'AI404', 7),
    ('TTNT_4Y_V1', 'AI405', 7),
    -- Kỳ 8
    ('TTNT_4Y_V1', 'AI406', 8),
    ('TTNT_4Y_V1', 'AI407', 8),
    ('TTNT_4Y_V1', 'AI408', 8),
    ('TTNT_4Y_V1', 'AI409', 8),
    ('TTNT_4Y_V1', 'AI410', 8)
) AS seed(curriculum_code, subject_code, recommend_semester);

