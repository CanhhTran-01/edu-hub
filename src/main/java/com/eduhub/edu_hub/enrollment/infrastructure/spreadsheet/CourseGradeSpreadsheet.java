package com.eduhub.edu_hub.enrollment.infrastructure.spreadsheet;

import com.eduhub.edu_hub.enrollment.api.dto.response.CourseGradeResponse;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassGradingResponse;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Mẫu ba cột cho một lớp học phần; số dòng Excel chỉ phục vụ báo lỗi, không định danh sinh viên. */
@Component
public class CourseGradeSpreadsheet {
    private static final int HEADER_ROW_INDEX = 4;
    private static final int MAX_DATA_ROWS = 1000;
    private static final long MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final List<String> HEADERS = List.of("MSSV", "Họ và tên", "Điểm thi học phần");

    public record ImportedCourseGrade(int rowNumber, String studentCode, BigDecimal finalExamScore) {}

    public record ImportedCourseGrades(List<ImportedCourseGrade> students, List<FieldErrorDetail> errors) {}

    public byte[] writeTemplate(ModuleClassGradingResponse moduleClass, List<CourseGradeResponse> students) {
        if (students.size() > MAX_DATA_ROWS)
            throw new EnrollmentException(
                    ErrorCode.COURSE_GRADE_TEMPLATE_FAILED,
                    List.of(new FieldErrorDetail(
                            "moduleClassId",
                            "Lớp vượt giới hạn mẫu 1000 dòng; hãy nhập từng cá nhân hoặc chia danh sách nhập")));
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Điểm thi học phần");
            sheet.createRow(0)
                    .createCell(0)
                    .setCellValue("NHẬP ĐIỂM THI HỌC PHẦN: có thể đổi thứ tự dòng; không để trống điểm của dòng nhập");
            var classMetadata = sheet.createRow(1);
            classMetadata.createCell(0).setCellValue("Mã lớp");
            classMetadata.createCell(1).setCellValue(moduleClass.moduleClassCode());
            var semesterMetadata = sheet.createRow(2);
            semesterMetadata.createCell(0).setCellValue("Kỳ học");
            semesterMetadata.createCell(1).setCellValue(moduleClass.semesterCode());
            var header = sheet.createRow(HEADER_ROW_INDEX);
            var headerStyle = workbook.createCellStyle();
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            for (int columnIndex = 0; columnIndex < HEADERS.size(); columnIndex++) {
                var cell = header.createCell(columnIndex);
                cell.setCellValue(HEADERS.get(columnIndex));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(columnIndex, (columnIndex == 1 ? 35 : 25) * 256);
            }
            var identifierStyle = workbook.createCellStyle();
            identifierStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            var scoreStyle = workbook.createCellStyle();
            scoreStyle.setDataFormat(workbook.createDataFormat().getFormat("0.###"));
            for (int studentIndex = 0; studentIndex < students.size(); studentIndex++) {
                var student = students.get(studentIndex);
                var studentRow = sheet.createRow(HEADER_ROW_INDEX + 1 + studentIndex);
                var studentCode = studentRow.createCell(0);
                studentCode.setCellStyle(identifierStyle);
                studentCode.setCellValue(student.studentCode());
                // Ghi chuỗi nguyên bản; tên bắt đầu bằng '=' không trở thành công thức Excel.
                studentRow.createCell(1).setCellValue(student.fullName());
                var examScore = studentRow.createCell(2);
                examScore.setCellStyle(scoreStyle);
                if (student.finalExamScore() != null)
                    examScore.setCellValue(student.finalExamScore().doubleValue());
            }
            sheet.createFreezePane(0, HEADER_ROW_INDEX + 1);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new EnrollmentException(ErrorCode.COURSE_GRADE_TEMPLATE_FAILED);
        }
    }

    public ImportedCourseGrades read(MultipartFile file, ModuleClassGradingResponse moduleClass) {
        if (file == null
                || file.isEmpty()
                || file.getSize() > MAX_FILE_BYTES
                || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw invalidFile("file", "Chỉ nhận tệp .xlsx không rỗng, tối đa 5 MB");
        try (var input = file.getInputStream();
                var workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() != 1)
                throw invalidFile("file", "Tệp phải có đúng một sheet theo mẫu nhập điểm");
            var sheet = workbook.getSheetAt(0);
            if (!"Mã lớp".equals(textCell(sheet.getRow(1), 0))
                    || !moduleClass.moduleClassCode().equals(textCell(sheet.getRow(1), 1))
                    || !"Kỳ học".equals(textCell(sheet.getRow(2), 0))
                    || !moduleClass.semesterCode().equals(textCell(sheet.getRow(2), 1)))
                throw invalidFile("file", "Mã lớp hoặc kỳ học không khớp lớp đang chọn (dòng 2–3)");
            for (int columnIndex = 0; columnIndex < HEADERS.size(); columnIndex++) {
                if (!HEADERS.get(columnIndex).equals(textCell(sheet.getRow(HEADER_ROW_INDEX), columnIndex)))
                    throw invalidFile(
                            "rows[5]", "Header dòng 5 phải là MSSV, Họ và tên, Điểm thi học phần theo đúng thứ tự");
            }
            if (sheet.getLastRowNum() - HEADER_ROW_INDEX > MAX_DATA_ROWS)
                throw invalidFile("file", "Tệp vượt quá 1000 vị trí dòng dữ liệu");
            for (var cell : sheet.getRow(HEADER_ROW_INDEX)) {
                if (cell.getColumnIndex() >= HEADERS.size() && !isEmptyCell(cell))
                    throw invalidFile("rows[5]", "Mẫu nhập chỉ nhận ba cột MSSV, Họ và tên, Điểm thi học phần");
            }
            List<ImportedCourseGrade> students = new ArrayList<>();
            List<FieldErrorDetail> errors = new ArrayList<>();
            for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                var studentRow = sheet.getRow(rowIndex);
                if (isEmptyRow(studentRow)) continue;
                int rowNumber = rowIndex + 1;
                for (var cell : studentRow) {
                    if (cell.getCellType() == CellType.FORMULA
                            || cell.getCellType() == CellType.ERROR
                            || (cell.getColumnIndex() >= HEADERS.size() && !isEmptyCell(cell)))
                        errors.add(new FieldErrorDetail(
                                "rows[" + rowNumber + "].columns",
                                "Không nhận công thức, ô lỗi hoặc dữ liệu ngoài ba cột mẫu"));
                }
                String studentCode = textCell(studentRow, 0);
                if (studentCode == null || studentCode.isBlank())
                    errors.add(new FieldErrorDetail(
                            "rows[" + rowNumber + "].studentCode", "MSSV bắt buộc là ô text không rỗng"));
                BigDecimal finalExamScore = readScore(studentRow, rowNumber, errors);
                students.add(new ImportedCourseGrade(rowNumber, studentCode, finalExamScore));
            }
            if (students.isEmpty()) throw invalidFile("file", "Tệp không có dòng điểm để nhập");
            return new ImportedCourseGrades(List.copyOf(students), List.copyOf(errors));
        } catch (EnrollmentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalidFile("file", "Không đọc được tệp XLSX; hãy tải lại mẫu và kiểm tra định dạng");
        }
    }

    private BigDecimal readScore(Row studentRow, int rowNumber, List<FieldErrorDetail> errors) {
        var cell = studentRow.getCell(2);
        try {
            String scoreText = cell != null && cell.getCellType() == CellType.NUMERIC
                    ? NumberToTextConverter.toText(cell.getNumericCellValue())
                    : textCell(studentRow, 2);
            if (scoreText != null && !scoreText.isBlank()) return new BigDecimal(scoreText.replace(',', '.'));
        } catch (NumberFormatException exception) {
            // Gom lỗi ô cùng lỗi MSSV/ràng buộc nghiệp vụ trước khi từ chối cả lô.
        }
        errors.add(new FieldErrorDetail(
                "rows[" + rowNumber + "].finalExamScore", "Điểm thi bắt buộc là số; ô trống không phải điểm 0"));
        return null;
    }

    private String textCell(Row spreadsheetRow, int columnIndex) {
        var cell = spreadsheetRow == null ? null : spreadsheetRow.getCell(columnIndex);
        return cell != null && cell.getCellType() == CellType.STRING
                ? cell.getStringCellValue().strip()
                : null;
    }

    private boolean isEmptyRow(Row spreadsheetRow) {
        if (spreadsheetRow == null) return true;
        for (var cell : spreadsheetRow) if (!isEmptyCell(cell)) return false;
        return true;
    }

    private boolean isEmptyCell(Cell cell) {
        return cell == null
                || cell.getCellType() == CellType.BLANK
                || (cell.getCellType() == CellType.STRING
                        && cell.getStringCellValue().isBlank());
    }

    private EnrollmentException invalidFile(String field, String message) {
        return new EnrollmentException(
                ErrorCode.COURSE_GRADE_IMPORT_INVALID, List.of(new FieldErrorDetail(field, message)));
    }
}
