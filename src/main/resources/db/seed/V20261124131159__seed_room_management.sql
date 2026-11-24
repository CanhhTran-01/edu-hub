-- 90 phòng tại hai cơ sở; mã đúng định dạng campus-buildingCode-roomNumber của API.
-- Các phòng 40 chỗ, INACTIVE và xóa mềm phục vụ lọc và kiểm tra sức chứa.
INSERT INTO rooms (room_code, room_name, campus, building_code, room_number, capacity, status, is_deleted,
                   created_at, updated_at, created_by, updated_by)
SELECT 'CS' || campus_number || '-T' || building_number || '-' || (floor_number * 100 + room_position),
       CASE WHEN room_position = 1 THEN 'Phòng thực hành ' ELSE 'Giảng đường ' END
           || (floor_number * 100 + room_position) || ' - Tòa T' || building_number,
       'CS' || campus_number, 'T' || building_number, (floor_number * 100 + room_position)::TEXT,
       (ARRAY[40,50,60,80,100])[room_position],
       CASE WHEN campus_number = 2 AND building_number = 3 AND floor_number = 3 AND room_position >= 3
            THEN 'INACTIVE' ELSE 'ACTIVE' END,
       campus_number = 2 AND building_number = 3 AND floor_number = 3 AND room_position = 5,
       TIMESTAMP '2016-06-01 02:00:00', TIMESTAMP '2016-06-01 02:00:00', 'demo-seed', 'demo-seed'
FROM generate_series(1,2) AS campus_number CROSS JOIN generate_series(1,3) AS building_number
CROSS JOIN generate_series(1,3) AS floor_number CROSS JOIN generate_series(1,5) AS room_position;
