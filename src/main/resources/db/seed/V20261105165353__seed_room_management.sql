-- Dữ liệu phòng học mẫu cho môi trường dev/test; database tự sinh ID.
-- Mã phòng theo dạng số phòng-tòa nhà, ví dụ phòng 102 ở tòa TA1 có mã 102-TA1.
-- Có 9 phòng đang dùng, 2 phòng ngừng dùng và 1 phòng đã xóa mềm.
-- Chưa tạo lịch học mẫu vì chức năng xếp lịch sẽ được triển khai sau.
INSERT INTO rooms (
    room_code, room_name, campus, building_code, room_number, capacity, status,
    is_deleted, created_at, updated_at
)
SELECT
    room_code, room_name, campus, building_code, room_number, capacity, status,
    is_deleted, CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
FROM (VALUES
    -- Cơ sở 1, tòa TA1: nhiều mức sức chứa, gồm cả phòng ngừng sử dụng.
    ('101-TA1', 'Phòng học 101 - Tòa TA1', 'CS1', 'TA1', '101', 80, 'ACTIVE', FALSE),
    ('102-TA1', 'Phòng học 102 - Tòa TA1', 'CS1', 'TA1', '102', 60, 'ACTIVE', FALSE),
    ('103-TA1', 'Phòng học 103 - Tòa TA1', 'CS1', 'TA1', '103', 40, 'ACTIVE', FALSE),
    ('201-TA1', 'Phòng học 201 - Tòa TA1', 'CS1', 'TA1', '201', 100, 'ACTIVE', FALSE),
    ('202-TA1', 'Phòng học 202 - Tòa TA1', 'CS1', 'TA1', '202', 120, 'ACTIVE', FALSE),
    ('203-TA1', 'Phòng học 203 - Tòa TA1', 'CS1', 'TA1', '203', 60, 'INACTIVE', FALSE),
    -- Cơ sở 1, tòa TA2: cùng số phòng với tòa khác nhưng mã và vị trí khác nhau.
    ('101-TA2', 'Phòng học 101 - Tòa TA2', 'CS1', 'TA2', '101', 50, 'ACTIVE', FALSE),
    ('102-TA2', 'Phòng học 102 - Tòa TA2', 'CS1', 'TA2', '102', 80, 'ACTIVE', FALSE),
    ('201-TA2', 'Phòng học 201 - Tòa TA2', 'CS1', 'TA2', '201', 100, 'ACTIVE', FALSE),
    ('202-TA2', 'Phòng học 202 - Tòa TA2', 'CS1', 'TA2', '202', 30, 'ACTIVE', FALSE),
    -- Cơ sở 2, tòa TB1: kiểm tra lọc cơ sở, trạng thái và ẩn dữ liệu xóa mềm.
    ('101-TB1', 'Phòng học 101 - Tòa TB1', 'CS2', 'TB1', '101', 60, 'INACTIVE', FALSE),
    ('102-TB1', 'Phòng học 102 - Tòa TB1', 'CS2', 'TB1', '102', 80, 'INACTIVE', TRUE)
) AS sample_rooms (
    room_code, room_name, campus, building_code, room_number, capacity, status, is_deleted
);
