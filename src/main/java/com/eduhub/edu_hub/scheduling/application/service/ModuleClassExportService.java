package com.eduhub.edu_hub.scheduling.application.service;

import com.eduhub.edu_hub.scheduling.api.dto.request.ExportModuleClassStudentsRequest;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassRosterException;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassExportColumn;
import com.eduhub.edu_hub.scheduling.infrastructure.spreadsheet.ModuleClassSpreadsheetWriter;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Xuất đồng bộ từ bản chụp đã kiểm tra quyền; tạo XLSX sau khi transaction đọc kết thúc. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleClassExportService {
    private final ModuleClassRosterService rosterService;
    private final ModuleClassSpreadsheetWriter spreadsheetWriter;
    private final Clock clock;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public byte[] exportStudents(Jwt principal, Long moduleClassId, ExportModuleClassStudentsRequest request) {
        var exportedAt = ZonedDateTime.now(clock);
        Object actorUserId = principal == null ? null : principal.getClaim("uid");
        try {
            // Kiểm tra lại tài khoản, vai trò và phân công ngay tại lần xuất; không tin lựa chọn cũ trên FE.
            var roster = rosterService.findRoster(principal, moduleClassId);
            var selectedColumns = requireExportColumns(request);
            byte[] spreadsheet = spreadsheetWriter.writeStudents(roster, selectedColumns, exportedAt);

            // TODO(AUDIT_LOG): Lưu lịch sử xuất khi có hạ tầng audit; GENERATED chỉ xác nhận tạo tệp,
            //  không chứng minh trình duyệt đã nhận hoặc lưu tệp thành công.
            log.info(
                    "Xuất danh sách lớp: actorUserId={}, exportedAt={}, semesterId={}, moduleClassId={}, columns={}, studentCount={}, outcome=GENERATED",
                    actorUserId,
                    exportedAt.toInstant(),
                    roster.moduleClass().getSemesterId(),
                    moduleClassId,
                    selectedColumns,
                    roster.students().size());
            return spreadsheet;
        } catch (BaseException exception) {
            log.warn(
                    "Xuất danh sách lớp: actorUserId={}, exportedAt={}, moduleClassId={}, outcome={}, errorCode={}",
                    actorUserId,
                    exportedAt.toInstant(),
                    moduleClassId,
                    exception.getErrorCode() == ErrorCode.UNAUTHORIZED ? "DENIED" : "FAILED",
                    exception.getErrorCode().getCode());
            throw exception;
        } catch (RuntimeException exception) {
            // Không log nội dung exception có thể chứa dữ liệu ô Excel hoặc thông tin sinh viên.
            log.error(
                    "Xuất danh sách lớp: actorUserId={}, exportedAt={}, moduleClassId={}, outcome=FAILED, exceptionType={}",
                    actorUserId,
                    exportedAt.toInstant(),
                    moduleClassId,
                    exception.getClass().getSimpleName());
            throw new ModuleClassRosterException(ErrorCode.MODULE_CLASS_EXPORT_FAILED, exception);
        }
    }

    private List<ModuleClassExportColumn> requireExportColumns(ExportModuleClassStudentsRequest request) {
        var columns = request == null ? null : request.getColumns();
        if (columns == null
                || columns.isEmpty()
                || columns.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(columns).size() != columns.size()
                || !columns.contains(ModuleClassExportColumn.STUDENT_CODE)
                || !columns.contains(ModuleClassExportColumn.FULL_NAME)) {
            throw new ModuleClassRosterException(
                    ErrorCode.VALIDATION_ERROR,
                    List.of(new FieldErrorDetail(
                            "columns", "Chọn các cột không trùng, bắt buộc có STUDENT_CODE và FULL_NAME")));
        }
        return List.copyOf(columns);
    }
}
