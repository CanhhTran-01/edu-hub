package com.eduhub.edu_hub.enrollment.infrastructure.spreadsheet;

import com.eduhub.edu_hub.enrollment.api.dto.response.ProcessScoreRosterResponse;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassGradingResponse;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Mẫu nhập cố định theo lớp; parser chỉ đọc dữ liệu, service đối chiếu lượt học và lưu nguyên tử. */
@Component
public class ProcessScoreSpreadsheet {
    private static final int HEADER_ROW_INDEX = 4;
    private static final int MAX_DATA_ROWS = 1000;
    private static final long MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final List<String> HEADERS = List.of("MSSV", "Họ và tên", "TP1", "TP2", "Ghi chú");

    public record ImportedProcessScore(
            int rowNumber, String studentCode, BigDecimal componentScore1, BigDecimal componentScore2, String note) {}

    public record ImportedProcessScores(List<ImportedProcessScore> students, List<FieldErrorDetail> errors) {}

    public byte[] writeTemplate(ProcessScoreRosterResponse roster) {
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Điểm quá trình");
            writeTemplateHeader(workbook, sheet, roster.moduleClass());
            var identifierStyle = workbook.createCellStyle();
            identifierStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            var scoreStyle = workbook.createCellStyle();
            scoreStyle.setDataFormat(workbook.createDataFormat().getFormat("0.###"));
            for (int studentIndex = 0; studentIndex < roster.students().size(); studentIndex++) {
                var student = roster.students().get(studentIndex);
                var studentRow = sheet.createRow(HEADER_ROW_INDEX + 1 + studentIndex);
                var codeCell = studentRow.createCell(0);
                codeCell.setCellStyle(identifierStyle);
                codeCell.setCellValue(student.studentCode());
                // Ghi ô chuỗi để không biến họ tên/ghi chú bắt đầu bằng '=' thành công thức.
                studentRow.createCell(1).setCellValue(student.fullName());
                writeScore(studentRow.createCell(2), student.componentScore1(), scoreStyle);
                writeScore(studentRow.createCell(3), student.componentScore2(), scoreStyle);
                studentRow.createCell(4).setCellValue(student.note());
            }
            sheet.createFreezePane(0, HEADER_ROW_INDEX + 1);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new EnrollmentException(ErrorCode.PROCESS_SCORE_TEMPLATE_FAILED);
        }
    }

    public ImportedProcessScores read(MultipartFile file, ModuleClassGradingResponse moduleClass) {
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
            validateSheetScopeAndColumns(sheet, moduleClass);
            // Gom lỗi từng dòng để service đối chiếu toàn bộ lượt học trước khi ghi cả lô.
            return readStudentRows(sheet);
        } catch (EnrollmentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalidFile("file", "Không đọc được tệp XLSX; hãy tải lại mẫu và kiểm tra định dạng");
        }
    }

    private BigDecimal readScore(
            Row studentRow, int columnIndex, int rowNumber, String field, List<FieldErrorDetail> errors) {
        var cell = studentRow.getCell(columnIndex);
        try {
            String scoreText = cell != null && cell.getCellType() == CellType.NUMERIC
                    ? NumberToTextConverter.toText(cell.getNumericCellValue())
                    : textCell(studentRow, columnIndex);
            if (scoreText != null && !scoreText.isBlank()) return new BigDecimal(scoreText.replace(',', '.'));
        } catch (NumberFormatException exception) {
            // Giữ lỗi theo dòng; service vẫn đối chiếu tất cả MSSV trước khi từ chối toàn bộ lô.
        }
        errors.add(new FieldErrorDetail(
                "rows[" + rowNumber + "]." + field,
                "Dòng " + rowNumber + ": " + (columnIndex == 2 ? "TP1" : "TP2") + " bắt buộc là số"));
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

    private void writeScore(Cell cell, BigDecimal score, CellStyle style) {
        cell.setCellStyle(style);
        if (score != null) cell.setCellValue(score.doubleValue());
    }

    private EnrollmentException invalidFile(String field, String message) {
        return new EnrollmentException(
                ErrorCode.PROCESS_SCORE_IMPORT_INVALID, List.of(new FieldErrorDetail(field, message)));
    }

    private void writeTemplateHeader(Workbook workbook, Sheet sheet, ModuleClassGradingResponse moduleClass) {
        sheet.createRow(0)
                .createCell(0)
                .setCellValue("NHẬP ĐIỂM QUÁ TRÌNH: mỗi dòng phải đủ TP1 và TP2; ghi chú trống giữ nguyên");
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
            sheet.setColumnWidth(columnIndex, (columnIndex == 1 || columnIndex == 4 ? 35 : 22) * 256);
        }
    }

    private void validateSheetScopeAndColumns(Sheet sheet, ModuleClassGradingResponse moduleClass) {
        if (!"Mã lớp".equals(textCell(sheet.getRow(1), 0))
                || !moduleClass.moduleClassCode().equals(textCell(sheet.getRow(1), 1))
                || !"Kỳ học".equals(textCell(sheet.getRow(2), 0))
                || !moduleClass.semesterCode().equals(textCell(sheet.getRow(2), 1)))
            throw invalidFile("file", "Mã lớp hoặc kỳ học trong tệp không khớp lớp đang chọn (dòng 2–3)");
        for (int columnIndex = 0; columnIndex < HEADERS.size(); columnIndex++) {
            if (!HEADERS.get(columnIndex).equals(textCell(sheet.getRow(HEADER_ROW_INDEX), columnIndex)))
                throw invalidFile(
                        "rows[5]", "Header dòng 5 phải là MSSV, Họ và tên, TP1, TP2, Ghi chú theo đúng thứ tự");
        }
        if (sheet.getLastRowNum() - HEADER_ROW_INDEX > MAX_DATA_ROWS)
            throw invalidFile("file", "Tệp vượt quá 1000 vị trí dòng dữ liệu");
    }

    private ImportedProcessScores readStudentRows(Sheet sheet) {
        List<ImportedProcessScore> students = new ArrayList<>();
        List<FieldErrorDetail> errors = new ArrayList<>();
        for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            var studentRow = sheet.getRow(rowIndex);
            if (isEmptyRow(studentRow)) continue;
            int rowNumber = rowIndex + 1;
            // Công thức/ô lỗi bị từ chối cả ở cột họ tên và cột thừa, không tính giá trị cache.
            for (var cell : studentRow) {
                if (cell.getCellType() == CellType.FORMULA
                        || cell.getCellType() == CellType.ERROR
                        || (cell.getColumnIndex() >= HEADERS.size() && !isEmptyCell(cell)))
                    errors.add(new FieldErrorDetail(
                            "rows[" + rowNumber + "].columns",
                            "Dòng " + rowNumber + ": không nhận công thức, ô lỗi hoặc dữ liệu ngoài cột mẫu"));
            }
            String studentCode = textCell(studentRow, 0);
            if (studentCode == null || studentCode.isBlank())
                errors.add(new FieldErrorDetail(
                        "rows[" + rowNumber + "].studentCode",
                        "Dòng " + rowNumber + ": MSSV bắt buộc là ô text không rỗng"));
            var firstScore = readScore(studentRow, 2, rowNumber, "componentScore1", errors);
            var secondScore = readScore(studentRow, 3, rowNumber, "componentScore2", errors);
            var noteCell = studentRow.getCell(4);
            String note = noteCell != null && noteCell.getCellType() == CellType.STRING
                    ? noteCell.getStringCellValue()
                    : null;
            if (!isEmptyCell(studentRow.getCell(4)) && note == null)
                errors.add(new FieldErrorDetail(
                        "rows[" + rowNumber + "].note", "Dòng " + rowNumber + ": ghi chú phải là ô text"));
            students.add(new ImportedProcessScore(rowNumber, studentCode, firstScore, secondScore, note));
        }
        if (students.isEmpty()) throw invalidFile("file", "Tệp không có dòng điểm để nhập");
        return new ImportedProcessScores(List.copyOf(students), List.copyOf(errors));
    }
}
