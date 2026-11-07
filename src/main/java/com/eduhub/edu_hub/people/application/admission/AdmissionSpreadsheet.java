package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.people.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class AdmissionSpreadsheet {
    private final AdmissionSettings settings;
    public static final List<String> HEADERS =
            List.of("ma_ho_so", "cccd", "ho_ten", "diem_dau_vao", "ngay_sinh", "gioi_tinh", "email", "so_dien_thoai");
    // Dòng 1-3: thông tin chung; dòng 4: hướng dẫn; dòng 5: tên cột; từ dòng 6: hồ sơ.
    public static final int HEADER_ROW_INDEX = 4;

    public record Scope(String majorCode, String majorName, String cohortCode) {}

    // đọc nội dung file upload lên
    public List<AdmissionApplicant> parse(MultipartFile file, Scope expectedScope) {

        // validate file trước khi xử lý
        if (file == null
                || file.isEmpty()
                || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
        }

        // Mở file xlsx upload bằng try-with-resources
        // input và workbook tự động được đóng khi kết thúc khối try, tránh rò rỉ bộ nhớ
        try (var input = file.getInputStream();
                var workbook = new XSSFWorkbook(input)) {

            // phải đúng 1 sheet
            if (workbook.getNumberOfSheets() != 1) {
                throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
            }

            // Không tính khối thông tin chung và hàng header vào giới hạn số dòng hồ sơ.
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() - HEADER_ROW_INDEX > settings.getMaxImportRows()) {
                throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
            }

            // Đọc hàng header, dựng bản đồ cột (map từng ô của hàng header với chỉ số cụ thể)

            // Apache POI tool giúp đọc value ô (Cell) dưới dạng String đúng như cách hiển thị trên Excel
            DataFormatter formatter = new DataFormatter(Locale.ROOT);

            // Sai ngành/khóa là lỗi toàn file, phải chặn trước khi đọc từng hồ sơ.
            validateScope(sheet, formatter, expectedScope);

            Map<String, Integer> columns = new HashMap<>(); // key=tên cột, value=chỉ số vị trí của cột (từ 0)

            Row header = sheet.getRow(HEADER_ROW_INDEX);
            if (header == null) {
                throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
            }

            // duyệt từng ô của hàng header để dựng chỉ số
            for (Cell cell : header) {
                rejectFormulaOrError(cell);
                String title = formatter.formatCellValue(cell).strip().toLowerCase(Locale.ROOT);
                if (columns.put(title, cell.getColumnIndex()) != null) {
                    // phát hiện cột trùng tên, nhả lỗi
                    throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
                }
            }

            // 4 cột đầu của HEADERS phải có, còn lại tùy chọn
            if (!columns.keySet().containsAll(HEADERS.subList(0, 4))) {
                throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
            }

            List<AdmissionApplicant> result = new ArrayList<>();

            // đọc từng dòng dữ liệu
            for (int index = HEADER_ROW_INDEX + 1; index <= sheet.getLastRowNum(); index++) {

                Row row = sheet.getRow(index);

                // bỏ qua dòng trống
                if (row == null || isEmpty(row, formatter)) {
                    continue;
                }

                AdmissionApplicant applicant = new AdmissionApplicant();

                // Lưu đúng số dòng vật lý trong Excel để Admin tìm được hồ sơ lỗi.
                applicant.setRowNumber(index + 1);

                try {
                    applicant.setApplicationCode(AdmissionText.key(text(row, columns, "ma_ho_so", formatter)));

                    Cell citizenCell = cell(row, columns, "cccd");
                    if (citizenCell != null
                            && citizenCell.getCellType() != CellType.STRING
                            && citizenCell.getCellType() != CellType.BLANK) {
                        throw new IllegalArgumentException("CCCD phải là ô văn bản để giữ số 0 đầu");
                    }

                    applicant.setCitizenId(AdmissionText.citizenId(text(row, columns, "cccd", formatter)));
                    applicant.setFullName(AdmissionText.optional(text(row, columns, "ho_ten", formatter)));
                    applicant.setEmail(AdmissionText.email(text(row, columns, "email", formatter)));
                    applicant.setPhone(AdmissionText.optional(text(row, columns, "so_dien_thoai", formatter)));
                    applicant.setGender(AdmissionText.optional(text(row, columns, "gioi_tinh", formatter)));

                    String score = text(row, columns, "diem_dau_vao", formatter);
                    if (score != null) {
                        applicant.setEntranceScore(new BigDecimal(score.replace(',', '.')));
                    }

                    /*
                     * Xử lý ngày sinh:
                     * - Trường hợp 1: Excel lưu dạng số chuẩn Date --> chuyển trực tiếp.
                     * - Trường hợp 2: Người dùng gõ text thủ công --> dùng hàm parseDate để ép kiểu.
                     */
                    Cell date = cell(row, columns, "ngay_sinh");
                    if (date != null && date.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(date)) {
                        applicant.setDateOfBirth(
                                date.getLocalDateTimeCellValue().toLocalDate());
                    } else {
                        String value = text(row, columns, "ngay_sinh", formatter);
                        if (value != null) applicant.setDateOfBirth(parseDate(value));
                    }

                    // Kiểm tra các ô công thức hoặc ô lỗi của hàng vừa xử lý
                    for (Cell value : row) {
                        if (value.getCellType() == CellType.FORMULA || value.getCellType() == CellType.ERROR) {
                            throw new IllegalArgumentException("Không chấp nhận ô công thức hoặc ô lỗi");
                        }
                    }

                } catch (RuntimeException exception) {

                    applicant.setState(AdmissionApplicant.State.INVALID);

                    applicant.setErrorMessage(
                            exception instanceof IllegalArgumentException
                                            && exception.getMessage() != null
                                            && exception.getMessage().startsWith("CCCD")
                                    ? exception.getMessage()
                                    : "Dữ liệu ô, điểm hoặc ngày sinh không hợp lệ; không dùng công thức");
                }

                result.add(applicant);
            }

            return result;

        } catch (BaseException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
        }
    }

    // Mỗi file dành cho đúng một ngành và một khóa đã chọn từ danh mục.
    public byte[] template(Scope scope) {
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sinh vien trung tuyen");

            metadata(sheet, 0, "ma_nganh", scope.majorCode());
            metadata(sheet, 1, "ten_nganh", scope.majorName());
            metadata(sheet, 2, "ma_khoa", scope.cohortCode());
            sheet.createRow(3)
                    .createCell(0)
                    .setCellValue("Giữ nguyên ma_nganh/ma_khoa; nhập danh sách sinh viên từ dòng 6.");

            Row row = sheet.createRow(HEADER_ROW_INDEX);

            for (int i = 0; i < HEADERS.size(); i++) {
                row.createCell(i).setCellValue(HEADERS.get(i)); // đặt value cho từng ô trong header
                sheet.setColumnWidth(i, 24 * 256); // Đặt độ rộng cho cột thứ i
            }

            CellStyle text = workbook.createCellStyle();

            // Đặt định dạng mặc định kiểu Text cho toàn bộ dữ liệu ở các cột thứ 0, 1, và 7
            text.setDataFormat(workbook.createDataFormat().getFormat("@")); // @ đại diện cho định dạng Text
            sheet.setDefaultColumnStyle(0, text);
            sheet.setDefaultColumnStyle(1, text);
            sheet.setDefaultColumnStyle(7, text);
            sheet.createFreezePane(0, HEADER_ROW_INDEX + 1);

            // Ghi dữ liệu và Trả về mảng Byte
            workbook.write(output);
            return output.toByteArray();

        } catch (IOException exception) {
            throw new BaseException(ErrorCode.UNEXPECTED_ERROR);
        }
    }

    private void validateScope(Sheet sheet, DataFormatter formatter, Scope expected) {
        for (int index = 0; index < HEADER_ROW_INDEX; index++) {
            Row row = sheet.getRow(index);
            if (row != null) {
                for (Cell cell : row) rejectFormulaOrError(cell);
            }
        }
        String majorCode = requiredMetadata(sheet, 0, "ma_nganh", formatter);
        String cohortCode = requiredMetadata(sheet, 2, "ma_khoa", formatter);
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (!AdmissionText.key(expected.majorCode()).equals(AdmissionText.key(majorCode))) {
            errors.add(new FieldErrorDetail(
                    "majorCode", "Ngành trong file: " + majorCode + "; ngành đã chọn: " + expected.majorCode()));
        }
        if (!AdmissionText.key(expected.cohortCode()).equals(AdmissionText.key(cohortCode))) {
            errors.add(new FieldErrorDetail(
                    "cohortCode", "Khóa trong file: " + cohortCode + "; khóa đã chọn: " + expected.cohortCode()));
        }
        if (!errors.isEmpty()) throw new AdmissionImportScopeException(errors);
        // ten_nganh chỉ hiển thị. Không dùng tên, tiền tố lớp hay dữ liệu Excel để đổi ngành của plan.
    }

    private String requiredMetadata(Sheet sheet, int index, String label, DataFormatter formatter) {
        Row row = sheet.getRow(index);
        if (row == null
                || !AdmissionText.key(label).equals(AdmissionText.key(formatter.formatCellValue(row.getCell(0))))) {
            throw new BaseException(ErrorCode.ADMISSION_IMPORT_METADATA_REQUIRED);
        }
        String value = AdmissionText.optional(formatter.formatCellValue(row.getCell(1)));
        if (value == null) throw new BaseException(ErrorCode.ADMISSION_IMPORT_METADATA_REQUIRED);
        return value;
    }

    private void metadata(Sheet sheet, int index, String label, String value) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private void rejectFormulaOrError(Cell cell) {
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) {
            throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
        }
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (java.time.format.DateTimeParseException exception) {
            return LocalDate.parse(
                    text,
                    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT));
        }
    }

    private boolean isEmpty(Row row, DataFormatter formatter) {
        for (Cell cell : row) if (!formatter.formatCellValue(cell).isBlank()) return false;
        return true;
    }

    private Cell cell(Row row, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        return index == null ? null : row.getCell(index);
    }

    private String text(Row row, Map<String, Integer> columns, String name, DataFormatter formatter) {
        Cell cell = cell(row, columns, name);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) {
            throw new IllegalArgumentException("Không chấp nhận công thức");
        }
        return AdmissionText.optional(formatter.formatCellValue(cell));
    }
}
