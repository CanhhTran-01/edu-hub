package com.eduhub.edu_hub.scheduling.infrastructure.spreadsheet;

import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassRosterResponse;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassRosterException;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassExportColumn;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Chỉ dựng XLSX từ dữ liệu đã tải, không truy vấn hoặc tính lại điểm. */
@Component
public class ModuleClassSpreadsheetWriter {
    private static final int HEADER_ROW_INDEX = 8;
    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX");

    public byte[] writeStudents(
            ModuleClassRosterResponse roster, List<ModuleClassExportColumn> columns, ZonedDateTime exportedAt) {
        try (var workbook = new XSSFWorkbook();
                var outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Danh sách sinh viên");
            var moduleClass = roster.moduleClass();
            var metadataStyle = workbook.createCellStyle();
            metadataStyle.setWrapText(true);
            writeMetadata(sheet, 0, columns.size(), "DANH SÁCH SINH VIÊN LỚP HỌC PHẦN", metadataStyle);
            writeMetadata(sheet, 1, columns.size(), "Kỳ học: " + moduleClass.getSemesterCode(), metadataStyle);
            writeMetadata(sheet, 2, columns.size(), "Lớp học phần: " + moduleClass.getModuleClassCode(), metadataStyle);
            writeMetadata(
                    sheet,
                    3,
                    columns.size(),
                    "Học phần: " + displayText(moduleClass.getSubjectCode()) + " - "
                            + displayText(moduleClass.getSubjectName()),
                    metadataStyle);
            writeMetadata(
                    sheet,
                    4,
                    columns.size(),
                    "Giảng viên: " + displayText(moduleClass.getLecturerName()),
                    metadataStyle);
            writeMetadata(
                    sheet,
                    5,
                    columns.size(),
                    "Số sinh viên: " + roster.students().size(),
                    metadataStyle);
            writeMetadata(
                    sheet,
                    6,
                    columns.size(),
                    "Thời điểm xuất: " + EXPORT_TIME_FORMAT.format(exportedAt),
                    metadataStyle);

            var headerStyle = workbook.createCellStyle();
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var identifierStyle = workbook.createCellStyle();
            identifierStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            var scoreStyle = workbook.createCellStyle();
            scoreStyle.setDataFormat(workbook.createDataFormat().getFormat("0.##"));

            var header = sheet.createRow(HEADER_ROW_INDEX);
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                var column = columns.get(columnIndex);
                var headerCell = header.createCell(columnIndex);
                headerCell.setCellValue(getColumnLabel(column));
                headerCell.setCellStyle(headerStyle);
                sheet.setColumnWidth(
                        columnIndex,
                        (column == ModuleClassExportColumn.FULL_NAME || column == ModuleClassExportColumn.NOTE
                                        ? 34
                                        : 22)
                                * 256);
            }

            for (int studentIndex = 0; studentIndex < roster.students().size(); studentIndex++) {
                var student = roster.students().get(studentIndex);
                var studentRow = sheet.createRow(HEADER_ROW_INDEX + 1 + studentIndex);
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    var cell = studentRow.createCell(columnIndex);
                    switch (columns.get(columnIndex)) {
                        case STUDENT_CODE -> {
                            cell.setCellStyle(identifierStyle);
                            cell.setCellValue(student.studentCode());
                        }
                        case FULL_NAME -> cell.setCellValue(student.fullName());
                        case COMPONENT_SCORE_1 -> writeScore(cell, student.componentScore1(), scoreStyle);
                        case COMPONENT_SCORE_2 -> writeScore(cell, student.componentScore2(), scoreStyle);
                        case PROCESS_SCORE -> writeScore(cell, student.processScore(), scoreStyle);
                        case NOTE -> cell.setBlank();
                    }
                }
            }
            if (roster.students().isEmpty())
                writeMetadata(sheet, HEADER_ROW_INDEX + 2, columns.size(), "Chưa có sinh viên đăng ký", metadataStyle);

            sheet.createFreezePane(0, HEADER_ROW_INDEX + 1);
            sheet.setAutoFilter(new CellRangeAddress(
                    HEADER_ROW_INDEX, HEADER_ROW_INDEX + roster.students().size(), 0, columns.size() - 1));
            sheet.setRepeatingRows(new CellRangeAddress(HEADER_ROW_INDEX, HEADER_ROW_INDEX, -1, -1));
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new ModuleClassRosterException(ErrorCode.MODULE_CLASS_EXPORT_FAILED, exception);
        }
    }

    private void writeMetadata(Sheet sheet, int rowIndex, int columnCount, String text, CellStyle style) {
        var metadataRow = sheet.createRow(rowIndex);
        metadataRow.setHeightInPoints(30);
        var metadataCell = metadataRow.createCell(0);
        // Luôn ghi ô chuỗi, kể cả tên/mã bắt đầu bằng '='; không diễn giải dữ liệu thành công thức.
        metadataCell.setCellValue(text);
        metadataCell.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, columnCount - 1));
    }

    private void writeScore(Cell cell, BigDecimal score, CellStyle style) {
        cell.setCellStyle(style);
        if (score == null) cell.setBlank();
        else cell.setCellValue(score.doubleValue());
    }

    private String displayText(String text) {
        return text == null ? "" : text;
    }

    private String getColumnLabel(ModuleClassExportColumn column) {
        return switch (column) {
            case STUDENT_CODE -> "MSSV";
            case FULL_NAME -> "Họ và tên";
            case COMPONENT_SCORE_1 -> "Điểm TP1";
            case COMPONENT_SCORE_2 -> "Điểm TP2";
            case PROCESS_SCORE -> "Điểm quá trình";
            case NOTE -> "Ghi chú";
        };
    }
}
