package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.response.AdmissionResponses.PlanResponse;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.*;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdmissionImportService {
    private final AdmissionPlanRepository plans;
    private final AdmissionApplicantRepository applicants;
    private final StudentAdmissionRepository admissions;
    private final StudentRepository students;
    private final IdentityFacade identity;
    private final AdmissionSpreadsheet spreadsheet;
    private final AdmissionIdentityPolicy policy;
    private final AdmissionView view;

    @Transactional
    public PlanResponse upload(Long planId, long expectedRevision, MultipartFile file) {

        // lấy plan và khóa lại: pessimistic lock và optimistic lock thủ công
        AdmissionPlan plan = AdmissionWorkflowSupport.locked(plans, planId, expectedRevision);

        // đang tạo sinh viên thì không cho upload lại
        if (plan.getState() == AdmissionPlan.State.CONFIRMING) {
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        // Ngành/khóa của plan là nguồn chính thức. File chỉ cung cấp thông tin đối chiếu,
        // không được đổi ngành/khóa của plan hoặc quyết định tiền tố lớp/MSSV.
        // Kiểm tra trước mọi thao tác xóa, thay thế hoặc gộp danh sách đã nhập.
        List<AdmissionApplicant> rows = spreadsheet.parse(
                file,
                new AdmissionSpreadsheet.Scope(
                        plan.getMajor().getMajorCode(),
                        plan.getMajor().getMajorName(),
                        plan.getCohort().getCohortCode()));

        // Lấy dòng cũ của plan, kiểm tra có ai nhập học thành công (ENROLLED) chưa
        List<AdmissionApplicant> previousRows = applicants.findByPlan_IdOrderByRowNumber(planId);
        boolean hasEnrolled = previousRows.stream().anyMatch(r -> r.getState() == AdmissionApplicant.State.ENROLLED);

        // validate các AdmissionApplicant đã parse từ file mới
        for (AdmissionApplicant row : rows) {

            row.setPlan(plan); // applican này thuộc về plant hiện tại

            if (row.getState() == AdmissionApplicant.State.INVALID) continue;

            List<String> errors = policy.validate(row);
            if (!errors.isEmpty()) mark(row, AdmissionApplicant.State.INVALID, String.join("; ", errors));
        }

        // Chỉ giữ lại những thí sinh đã qua bước parse và validate
        var validRows = rows.stream()
                .filter(r -> r.getState() == AdmissionApplicant.State.VALID)
                .toList();

        Map<String, StudentAdmission> existingAdmissionsByApplicationCode = new HashMap<>();
        Map<String, List<Student>> existingStudentsByCitizenId = new HashMap<>();

        for (int offset = 0; offset < validRows.size(); offset += 500) {
            // Chia nhỏ query để không vượt quá giới hạn tham số của PostgreSQL khi import dữ liệu lớn
            // Tra DB theo lô 500
            List<AdmissionApplicant> chunks = validRows.subList(offset, Math.min(offset + 500, validRows.size()));

            // Lưu trước các StudentAdmission theo mã hồ sơ để truy vấn, tránh N+1 query
            // dùng để xem hồ sơ này đã được import trước đó chưa ?
            admissions
                    .findByApplicationCodeIn(chunks.stream()
                            .map(AdmissionApplicant::getApplicationCode)
                            .distinct()
                            .toList())
                    .forEach(value -> existingAdmissionsByApplicationCode.put(value.getApplicationCode(), value));

            //  Lưu trước các StudentAdmission theo CCCD để truy vấn, tránh N+1 query
            // dùng để biết người này đã có trong hệ thống chưa ?
            students.findByCitizenIdIn(chunks.stream()
                            .map(AdmissionApplicant::getCitizenId)
                            .distinct()
                            .toList())
                    .forEach(value -> existingStudentsByCitizenId
                            .computeIfAbsent(value.getCitizenId(), key -> new ArrayList<>())
                            .add(value));
        }

        // phân loại validRows thành: mới, đã tồn tại (EXISTING), hoặc xung đột (CONFLICT)
        Map<String, AdmissionApplicant> seenCodes = new HashMap<>();
        Map<String, List<AdmissionApplicant>> seenCitizens =
                validRows.stream().collect(Collectors.groupingBy(AdmissionApplicant::getCitizenId));

        for (AdmissionApplicant row : validRows) {

            // lấy dữ liệu lên check
            AdmissionApplicant previous = seenCodes.putIfAbsent(row.getApplicationCode(), row);
            StudentAdmission exist = existingAdmissionsByApplicationCode.get(row.getApplicationCode());
            List<Student> matches = existingStudentsByCitizenId.getOrDefault(row.getCitizenId(), List.of()).stream()
                    .collect(Collectors.toMap(Student::getId, Function.identity(), (first, second) -> first))
                    .values()
                    .stream()
                    .toList();

            // CCCD chưa có trong hệ thống, đây là thí sinh mới hoàn toàn
            if (matches.isEmpty()) continue;

            // Trùng mã hồ sơ ngay trong file
            if (previous != null) {
                if (policy.same(previous, row) && previous.getState() != AdmissionApplicant.State.CONFLICT) {
                    mark(row, AdmissionApplicant.State.EXISTING, "Dòng trùng đã tồn tại trong file");
                } else {
                    mark(previous, AdmissionApplicant.State.CONFLICT, "Cùng mã hồ sơ, khác dữ liệu trong file");
                    mark(row, AdmissionApplicant.State.CONFLICT, "Cùng mã hồ sơ, khác dữ liệu trong file");
                }
                continue;
            }

            // Mã hồ sơ đã có trong DB
            if (exist != null) {
                row.setStudent(exist.getStudent());
                row.setReuseAccount(true);
                mark(
                        row,
                        policy.same(row, exist) ? AdmissionApplicant.State.EXISTING : AdmissionApplicant.State.CONFLICT,
                        policy.same(row, exist) ? "Hồ sơ đã tồn tại" : "Cùng mã hồ sơ nhưng khác dữ liệu hoặc CCCD");
                continue;
            }

            // Chưa có mã hồ sơ, nhưng CCCD khớp sinh viên cũ
            if (matches.size() != 1
                    || matches.getFirst().isDeleted()
                    || matches.getFirst().getUserId() == null) {
                mark(row, AdmissionApplicant.State.CONFLICT, "Danh tính cũ đã tồn tại trong hệ thống");
                continue;
            }

            // Khớp đúng một sinh viên, kiểm tra tài khoản
            Student student = matches.getFirst();
            try {
                var account = identity.getStudentAccount(student.getUserId());
                row.setStudent(student);
                row.setReuseAccount(true);

                if (account.locked()) {
                    row.setWarningMessage("Tài khoản cũ đang khóa/ngừng hoạt động; hệ thống giữ nguyên trạng thái");
                }

                if (!AdmissionText.key(student.getFullName()).equals(AdmissionText.key(row.getFullName()))) {
                    row.setWarningMessage("Họ tên khác hồ sơ danh tính cũ; giữ nguyên hồ sơ gốc"
                            + (row.getWarningMessage() == null ? "" : "; " + row.getWarningMessage()));
                }

            } catch (BaseException exception) {
                mark(row, AdmissionApplicant.State.CONFLICT, "Không thể dùng lại tài khoản cũ; Admin cần kiểm tra");
            }
        }

        // Quét xung đột chéo

        // Một CCCD có nhiều mã hồ sơ
        seenCitizens.values().stream()
                .filter(group -> group.stream()
                                .map(AdmissionApplicant::getApplicationCode)
                                .distinct()
                                .count()
                        > 1)
                .forEach(group -> group.forEach(row ->
                        mark(row, AdmissionApplicant.State.CONFLICT, "Một CCCD có nhiều mã hồ sơ ở cùng đợt phân")));

        // Lan truyền xung đột theo mã hồ sơ
        // Nhóm các dòng theo mã hồ sơ; nếu có ít nhất một dòng CONFLICT thì mọi dòng cùng mã đều thành CONFLICT
        validRows.stream().collect(Collectors.groupingBy(AdmissionApplicant::getApplicationCode)).values().stream()
                .filter(group -> group.stream().anyMatch(row -> row.getState() == AdmissionApplicant.State.CONFLICT))
                .forEach(group -> group.forEach(row ->
                        mark(row, AdmissionApplicant.State.CONFLICT, "Cùng mã hồ sơ nhưng có dữ liệu xung đột")));

        // đã có sinh viên nhập học từ lần import trước
        // Khi kế hoạch đã có người nhập học thành công, không được xóa sạch dữ liệu cũ mà phải gộp.
        if (hasEnrolled) {
            int nextRow = previousRows.stream()
                            .mapToInt(AdmissionApplicant::getRowNumber)
                            .max()
                            .orElse(1)
                    + 1;
            Map<String, AdmissionApplicant> previousByCode = new HashMap<>();
            Set<String> mergedCodes = new HashSet<>(); // tập mã đã xử lý, chống xử lý lặp.

            previousRows.forEach(row -> {
                if (row.getApplicationCode() != null) previousByCode.putIfAbsent(row.getApplicationCode(), row);
            });

            for (AdmissionApplicant incoming : rows) {

                AdmissionApplicant previous = previousByCode.get(incoming.getApplicationCode());

                // đã gộp rồi, bỏ qua các bản lặp của cùng mã trong file mới.
                if (previous != null && !mergedCodes.add(incoming.getApplicationCode())) continue;

                // dòng cũ đã ENROLLED, có 3 case:
                if (previous != null && previous.getState() == AdmissionApplicant.State.ENROLLED) {

                    // dòng mới là EXISTING và giống dòng cũ --> bỏ qua
                    if (incoming.getState() == AdmissionApplicant.State.EXISTING && policy.same(previous, incoming))
                        continue;

                    // ngược lại, đánh CONFLICT, gán số dòng mới và thêm vào danh sách để Admin thấy
                    mark(incoming, AdmissionApplicant.State.CONFLICT, "Không ghi đè hồ sơ đã nhập học thành công");
                    incoming.setRowNumber(nextRow++);
                    previousRows.add(incoming);

                } else if (previous != null) {
                    // chép dữ liệu mới đè lên dòng cũ, trừ các trường định danh/audit
                    BeanUtils.copyProperties(
                            incoming,
                            previous,
                            "id",
                            "createdAt",
                            "updatedAt",
                            "createdBy",
                            "updatedBy",
                            "rowNumber",
                            "plan",
                            "deleted");
                } else {
                    incoming.setRowNumber(nextRow++);
                    previousRows.add(incoming);
                }
            }

            rows = previousRows; // danh sách lưu về là danh sách đã gộp (dòng cũ được cập nhật + dòng mới)

        } else {
            // Xóa toàn bộ dòng import cũ của kế hoạch rồi import lại từ đầu
            applicants.deleteByPlan_Id(planId);
            applicants.flush();
        }

        // Lưu toàn bộ dòng (kể cả INVALID, CONFLICT, để người dùng xem lại).
        applicants.saveAll(rows);
        applicants.flush();

        // Tổng số sinh viên = số dòng được tính vào đợt
        plan.setTotalStudents(
                (int) rows.stream().filter(AdmissionApplicant::isIncluded).count());

        // Trạng thái kế hoạch
        if (hasEnrolled && rows.stream().noneMatch(AdmissionApplicant::isPending)) {
            // Đã có người nhập học và không còn dòng nào đang chờ --> CONFIRMED (hoàn tất)
            plan.setState(AdmissionPlan.State.CONFIRMED);
        } else {
            // nếu chưa chọn số lớp --> DRAFT; đã chọn --> CLASSES_READY
            plan.setState(
                    plan.getSelectedClassCount() == null
                            ? AdmissionPlan.State.DRAFT
                            : AdmissionPlan.State.CLASSES_READY);
        }

        // đánh dấu kế hoạch thay đổi
        AdmissionWorkflowSupport.changed(plan);

        // TODO(AUDIT_LOG): Record file import, exclusions, conflicts and retry decisions.

        log.info(
                "Imported admission candidates: planId={}, rows={}, eligible={}",
                planId,
                rows.size(),
                plan.getTotalStudents());

        return view.response(plan);
    }

    private void mark(AdmissionApplicant row, AdmissionApplicant.State state, String error) {
        row.setState(state);
        row.setErrorMessage(error);
    }
}
