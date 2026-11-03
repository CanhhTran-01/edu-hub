# Test luồng quản lý lớp học phần

`module-class-planning.json` là dữ liệu mẫu của scheduling, chỉ được nạp ở profile `dev`/`test`.
CTĐT và lớp học phần đọc/ghi database thật; sinh viên/lịch sử học dùng fixture để lập kế hoạch mở môn.
Mở lớp không yêu cầu giảng viên và không gọi nguồn People. Lớp mới có lecturerId/lecturerName=null,
dataSource=DATABASE. Fixture giảng viên chỉ còn phục vụ hiển thị lớp cũ đã được phân công.
Không tạo bảng thay cho People. Khi ghép BE1, thay adapter qua Facade và đối soát ID giảng viên cũ.
Ngoài dev/test, các chức năng cần nguồn mẫu chưa có adapter thật sẽ trả 4317/4325 tương ứng.

## Các API hiện có

- `GET /api/v1/semesters/{semesterId}/opening-subjects`: nhu cầu bắt buộc theo CTĐT.
- `POST /api/v1/semesters/{semesterId}/module-classes/batch`: mở nhiều môn/lớp trong một transaction.
- `GET /api/v1/module-classes?semesterId=...`: danh sách phân trang, lọc thêm subjectId/lecturerId/status.
- `GET /api/v1/module-classes/{id}`: chi tiết lớp đã tạo.
- `PATCH /api/v1/module-classes/{id}`: cập nhật sức chứa, bắt buộc lý do.
- `POST /api/v1/module-classes/{id}/cancel`: hủy lớp chưa từng phát sinh đăng ký.

GET danh sách hỗ trợ thêm `keyword` tìm một phần mã lớp, không phân biệt hoa/thường;
`%` và `_` là ký tự cần tìm theo nghĩa literal, kết hợp AND với các bộ lọc khác.

Lấy ID kỳ/môn qua API, không cố định ID từ SQL seed. Ví dụ body mở hai lớp cho cùng môn:

```json
{
  "subjects": [
    {
      "subjectId": 1,
      "confirmAdditionalClasses": false,
      "classes": [
        {"maxCapacity": 80},
        {"maxCapacity": 50}
      ]
    }
  ]
}
```

Sức chứa mặc định 80 nếu bỏ trường, chỉ nhận số nguyên 1–80. Mỗi môn xuất hiện một lần trong lô;
số phần tử `classes` là số lớp muốn mở. Nếu môn đã có lớp chưa hủy, API trả 409/code 4313
kèm `errors` chỉ ra môn; gửi lại toàn bộ lô với `confirmAdditionalClasses=true` cho các môn đó.
Không có lớp nào được lưu khi lô còn lỗi hoặc chưa xác nhận.

Lớp mới có mã `MC_<semesterId>_<subjectCode>_<STT từ 001>`, trạng thái `UNSCHEDULED`,
chưa có giảng viên, sĩ số 0, lịch rỗng. STT tiếp tục qua cả lớp đã hủy/ẩn. Khóa kỳ chặn mở lớp; hết hạn đăng ký
không đồng nghĩa kỳ bị khóa. Qua ngày kết thúc kỳ mới bị chặn theo thời gian Việt Nam.
Đổi ngày bắt đầu/kết thúc của kỳ có lớp chưa hủy cần `confirmDateChange=true` ở API cập nhật kỳ.

## Đối chiếu dữ liệu mẫu

- 107 sinh viên: 96 sinh viên nhập học 2026, chia đều bốn CTĐT, cùng 11 tình huống biên.
- Fixture còn 9 giảng viên để hiển thị lớp cũ; luồng mở lớp không sử dụng các ID này.
- Ở kỳ `HK_1_2026_2027`, CS101 có 100 sinh viên: 96 đúng kỳ, 2 học lại, 2 bỏ lỡ; gợi ý 2 lớp.
- SV đã đạt ở bất kỳ lần học nào không tính vào nhu cầu bắt buộc, kể cả sau đó cải thiện bị rớt.
- Enrollment đang học được loại; đã hủy không được coi là chưa từng đăng ký. Bỏ qua lịch sử ở kỳ tương lai.
- Chỉ suy ra bỏ lỡ khi nguồn lịch sử đầy đủ (`historyComplete=true`); nguồn thiếu trả 503/code 4317.
- Nhu cầu là tổng số SV cần học, chưa trừ sức chứa lớp đã mở. `existingClassCount` đếm lớp chưa hủy.

TODO: nguồn dữ liệu thật qua Facade, danh tính người thao tác/audit database, cấu hình sức chứa,
xếp lịch và công bố lớp, xác nhận học cải thiện trong enrollment.

Bộ migration đã đánh lại version V20261103130001–V20261103130007 theo thứ tự phụ thuộc,
giữ nguyên nội dung SQL. Nhóm thống nhất dùng DB dev có thể tạo lại: cần tạo lại DB trước khi khởi động app.
Migration cuối `V20261103130007__allow_unassigned_module_class_lecturer.sql` cho phép lecturer_id=NULL.

## Cập nhật và hủy lớp bằng Postman

Ví dụ PATCH `/api/v1/module-classes/101` (thay ID bằng kết quả mở lớp):

```json
{
  "maxCapacity": 60,
  "reason": "Điều chỉnh quy mô lớp"
}
```

PATCH bắt buộc maxCapacity và reason. Sức chứa không có giá trị mặc định, chỉ nhận số nguyên 1–80
và phải >= currentSize. Lỗi 409/4322 có errors cho biết sĩ số hiện có.
Đổi sức chứa giữ nguyên giảng viên và trạng thái lớp, áp dụng cho UNSCHEDULED/SCHEDULED/OPEN.
Phân công/đổi giảng viên, xếp lịch và mở đăng ký thuộc các use-case riêng làm sau.

Ví dụ POST `/api/v1/module-classes/101/cancel`:

```json
{"reason": "Không đủ nhu cầu mở lớp"}
```

Chỉ hủy khi currentSize=0 và không có bất kỳ Enrollment nào, kể cả DROPPED/đã ẩn.
Lỗi 409/4323 có số sinh viên hiện có hoặc số bản ghi đăng ký/sinh viên liên quan trong errors.
Hủy thành công trả HTTP 200, status=CANCELLED, giữ deleted=false; GET vẫn xem được lớp,
mã và STT được giữ. Không có endpoint xóa hoặc khôi phục. Sửa/hủy lại lớp CANCELLED trả 409/4321.
Kỳ đã qua endDate hoặc isLocked=true chặn cả sửa/hủy. Thay đổi mã, môn, kỳ, sĩ số không thuộc PATCH.
Lý do và dữ liệu trước/sau được ghi application log; audit database/người thao tác vẫn TODO.

`module-class-enrollments.json` là dataset riêng cho ràng buộc hủy lớp, chỉ dùng dev/test.
Nó đếm theo moduleClassCode, không dùng Attempt theo học phần của planning để suy ra đăng ký theo lớp:

| Mã lớp mẫu | Lịch sử fixture | Có thể hủy khi sĩ số 0? |
|---|---|---|
| MC_9_CS101_001 | Không có bản ghi | Có |
| MC_9_CS101_002 | Một Enrollment DROPPED | Không |
| MC_9_CS101_003 | Một Enrollment DROPPED đã ẩn | Không |
| MC_9_CS102_001 | Hai Enrollment COMPLETED/FAILED | Không |

Mã lớp cần khớp dữ liệu thực tế từ API; nếu semesterId khác 9 thì chỉnh mã trong fixture và chạy lại app.
JSON khai báo toàn bộ lịch sử mô phỏng với historyComplete=true: lớp không liệt kê có 0 đăng ký
TRONG DATASET MẪU. Không phải kết luận về Enrollment thật; không tạo/sửa Enrollment hoặc điểm trong DB.
Đặt historyComplete=false thì hủy trả 503/4325. Ngoài dev/test cũng trả 503/4325 khi chưa có adapter thật.

TODO(ENROLLMENT_INTEGRATION): thay query fixture bằng EnrollmentFacade đếm cả bản ghi đã hủy/ẩn;
luồng đăng ký phải khóa cùng hàng lớp, kiểm tra trạng thái và đồng bộ currentSize trước khi commit.
TODO(CLASS_SCHEDULE_INTEGRATION): ngừng hiệu lực lịch liên quan khi hủy lớp.
TODO(TEACHING_SCHEDULE): dành GET `/api/v1/module-classes/lecturers` cho danh sách giảng viên
phục vụ xếp lịch dạy; triển khai sau, chưa khôi phục endpoint trong luồng quản lý lớp hiện tại.
