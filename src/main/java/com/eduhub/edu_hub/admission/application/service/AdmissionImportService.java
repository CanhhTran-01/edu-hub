package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.application.command.AdmissionImportScope;
import com.eduhub.edu_hub.admission.application.mapper.AdmissionApplicantMapper;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.PlanResult;
import com.eduhub.edu_hub.admission.application.validator.AdmissionApplicantValidator;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.StudentAdmission;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionIdentityPolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionPlanPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.admission.domain.repository.StudentAdmissionRepository;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetReader;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionStudentInfo;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.PersonText;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final PeopleFacade.Admission people;
    private final IdentityFacade identity;
    private final CurriculumFacade.Majors majors;
    private final AdmissionSpreadsheetReader spreadsheet;
    private final AdmissionIdentityPolicy policy;
    private final AdmissionApplicantValidator validator;
    private final AdmissionApplicantMerger merger;
    private final AdmissionApplicantMapper applicantMapper;
    private final AdmissionPlanQueryService view;

    @Transactional
    public PlanResult upload(Long planId, long expectedRevision, MultipartFile file) {

        // lấy plan và khóa lại: pessimistic lock và optimistic lock thủ công
        AdmissionPlan plan = AdmissionPlanLoader.locked(plans, planId, expectedRevision);

        // đang tạo sinh viên thì không cho upload lại
        if (plan.getState() == AdmissionPlanStatus.CONFIRMING) {
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        // Ngành/khóa của plan là nguồn chính thức. File chỉ cung cấp thông tin đối chiếu,
        // không được đổi ngành/khóa của plan hoặc quyết định tiền tố lớp/MSSV.
        // Kiểm tra trước mọi thao tác xóa, thay thế hoặc gộp danh sách đã nhập.
        var major = majors.requireMajor(plan.getMajorId());
        var cohort = people.requireCohort(plan.getCohortId());
        List<AdmissionApplicant> rows = new ArrayList<>(spreadsheet
                .parse(
                        file,
                        AdmissionImportScope.builder()
                                .majorCode(major.majorCode())
                                .majorName(major.majorName())
                                .cohortCode(cohort.cohortCode())
                                .build())
                .stream()
                .map(applicantMapper::fromImportRow)
                .toList());

        // Lấy dòng cũ của plan, kiểm tra có ai nhập học thành công (ENROLLED) chưa
        List<AdmissionApplicant> previousRows = applicants.findByPlan_IdOrderByRowNumber(planId);
        boolean hasEnrolled = previousRows.stream().anyMatch(r -> r.getState() == AdmissionApplicantStatus.ENROLLED);

        // validate các AdmissionApplicant đã parse từ file mới
        for (AdmissionApplicant row : rows) {

            row.setPlan(plan); // applican này thuộc về plant hiện tại

            if (row.getState() == AdmissionApplicantStatus.INVALID) continue;

            List<String> errors = validator.validate(row);
            if (!errors.isEmpty()) mark(row, AdmissionApplicantStatus.INVALID, String.join("; ", errors));
        }

        // Chỉ giữ lại những thí sinh đã qua bước parse và validate
        var validRows = rows.stream()
                .filter(r -> r.getState() == AdmissionApplicantStatus.VALID)
                .toList();

        Map<String, StudentAdmission> existingAdmissionsByApplicationCode = new HashMap<>();
        Map<String, List<AdmissionStudentInfo>> existingStudentsByCitizenId = new HashMap<>();

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
            people.findStudentsByCitizenIds(chunks.stream()
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
            List<AdmissionStudentInfo> matches = existingStudentsByCitizenId
                    .getOrDefault(row.getCitizenId(), List.of())
                    .stream()
                    .collect(Collectors.toMap(
                            AdmissionStudentInfo::getId, Function.identity(), (first, second) -> first))
                    .values()
                    .stream()
                    .toList();

            // CCCD chưa có trong hệ thống, đây là thí sinh mới hoàn toàn
            // TODO(ADMISSION_IMPORT_VALIDATION_ORDER): Kiểm tra mã hồ sơ trước nhánh CCCD mới; lỗi có từ trước đợt tách
            // module.
            if (matches.isEmpty()) continue;

            // Trùng mã hồ sơ ngay trong file
            if (previous != null) {
                if (policy.same(previous, row) && previous.getState() != AdmissionApplicantStatus.CONFLICT) {
                    mark(row, AdmissionApplicantStatus.EXISTING, "Dòng trùng đã tồn tại trong file");
                } else {
                    mark(previous, AdmissionApplicantStatus.CONFLICT, "Cùng mã hồ sơ, khác dữ liệu trong file");
                    mark(row, AdmissionApplicantStatus.CONFLICT, "Cùng mã hồ sơ, khác dữ liệu trong file");
                }
                continue;
            }

            // Mã hồ sơ đã có trong DB
            if (exist != null) {
                row.setStudentId(exist.getStudentId());
                row.setReuseAccount(true);
                mark(
                        row,
                        policy.same(row, exist) ? AdmissionApplicantStatus.EXISTING : AdmissionApplicantStatus.CONFLICT,
                        policy.same(row, exist) ? "Hồ sơ đã tồn tại" : "Cùng mã hồ sơ nhưng khác dữ liệu hoặc CCCD");
                continue;
            }

            // Chưa có mã hồ sơ, nhưng CCCD khớp sinh viên cũ
            if (matches.size() != 1
                    || matches.getFirst().isDeleted()
                    || matches.getFirst().getUserId() == null) {
                mark(row, AdmissionApplicantStatus.CONFLICT, "Danh tính cũ đã tồn tại trong hệ thống");
                continue;
            }

            // Khớp đúng một sinh viên, kiểm tra tài khoản
            AdmissionStudentInfo student = matches.getFirst();
            try {
                var account = identity.getStudentAccount(student.getUserId());
                row.setStudentId(student.getId());
                row.setReuseAccount(true);

                if (account.locked()) {
                    row.setWarningMessage("Tài khoản cũ đang khóa/ngừng hoạt động; hệ thống giữ nguyên trạng thái");
                }

                if (!PersonText.key(student.getFullName()).equals(PersonText.key(row.getFullName()))) {
                    row.setWarningMessage("Họ tên khác hồ sơ danh tính cũ; giữ nguyên hồ sơ gốc"
                            + (row.getWarningMessage() == null ? "" : "; " + row.getWarningMessage()));
                }

            } catch (BaseException exception) {
                mark(row, AdmissionApplicantStatus.CONFLICT, "Không thể dùng lại tài khoản cũ; Admin cần kiểm tra");
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
                        mark(row, AdmissionApplicantStatus.CONFLICT, "Một CCCD có nhiều mã hồ sơ ở cùng đợt phân")));

        // Lan truyền xung đột theo mã hồ sơ
        // Nhóm các dòng theo mã hồ sơ; nếu có ít nhất một dòng CONFLICT thì mọi dòng cùng mã đều thành CONFLICT
        validRows.stream().collect(Collectors.groupingBy(AdmissionApplicant::getApplicationCode)).values().stream()
                .filter(group -> group.stream().anyMatch(row -> row.getState() == AdmissionApplicantStatus.CONFLICT))
                .forEach(group -> group.forEach(row ->
                        mark(row, AdmissionApplicantStatus.CONFLICT, "Cùng mã hồ sơ nhưng có dữ liệu xung đột")));

        // đã có sinh viên nhập học từ lần import trước
        // Khi kế hoạch đã có người nhập học thành công, không được xóa sạch dữ liệu cũ mà phải gộp.
        if (hasEnrolled) {
            rows = merger.merge(previousRows, rows);
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
            plan.setState(AdmissionPlanStatus.CONFIRMED);
        } else {
            // nếu chưa chọn số lớp --> DRAFT; đã chọn --> CLASSES_READY
            plan.setState(
                    plan.getSelectedClassCount() == null
                            ? AdmissionPlanStatus.DRAFT
                            : AdmissionPlanStatus.CLASSES_READY);
        }

        // đánh dấu kế hoạch thay đổi
        AdmissionPlanPolicy.changed(plan);

        // TODO(AUDIT_LOG): Ghi lần import, hồ sơ bị loại/xung đột và quyết định xử lý lại sau commit.

        log.info(
                "Imported admission candidates: planId={}, rows={}, eligible={}",
                planId,
                rows.size(),
                plan.getTotalStudents());

        return view.response(plan);
    }

    private void mark(AdmissionApplicant row, AdmissionApplicantStatus state, String error) {
        row.setState(state);
        row.setErrorMessage(error);
    }
}
