package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.api.dto.request.ImportAdmissionApplicantsRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.application.mapper.AdmissionApplicantImportMapper;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.StudentAdmission;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionApplicantMergePolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionApplicantValidationPolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionIdentityPolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionPlanPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.StudentAdmissionRepository;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetReader;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetScope;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionStudentInfo;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdmissionImportService {
    private final AdmissionPlanLockService planLockService;
    private final AdmissionApplicantRepository applicantRepository;
    private final StudentAdmissionRepository studentAdmissionRepository;
    private final PeopleFacade.Admission peopleAdmissionFacade;
    private final IdentityFacade identityFacade;
    private final CurriculumFacade.Majors majorCatalogFacade;
    private final AdmissionSpreadsheetReader spreadsheetReader;
    private final AdmissionIdentityPolicy identityPolicy;
    private final AdmissionApplicantValidationPolicy applicantValidationPolicy;
    private final AdmissionApplicantMergePolicy applicantMergePolicy;
    private final AdmissionApplicantImportMapper applicantMapper;
    private final AdmissionPlanQueryService planQueryService;

    @Transactional
    public AdmissionPlanResponse importApplicants(Long planId, ImportAdmissionApplicantsRequest request) {

        // lấy plan và khóa lại: pessimistic lock và optimistic lock thủ công
        AdmissionPlan plan = planLockService.lockPlanAtRevision(planId, request.expectedRevision());

        // đang tạo sinh viên thì không cho upload lại
        if (plan.getState() == AdmissionPlanStatus.CONFIRMING) {
            throw new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        // Ngành/khóa của plan là nguồn chính thức. File chỉ cung cấp thông tin đối chiếu,
        // không được đổi ngành/khóa của plan hoặc quyết định tiền tố lớp/MSSV.
        // Kiểm tra trước mọi thao tác xóa, thay thế hoặc gộp danh sách đã nhập.
        var major = majorCatalogFacade.requireMajor(plan.getMajorId());
        var cohort = peopleAdmissionFacade.requireCohort(plan.getCohortId());
        List<AdmissionApplicant> importedApplicants = new ArrayList<>(spreadsheetReader
                .parse(
                        request.file(),
                        AdmissionSpreadsheetScope.builder()
                                .majorCode(major.majorCode())
                                .majorName(major.majorName())
                                .cohortCode(cohort.cohortCode())
                                .build())
                .stream()
                .map(applicantMapper::fromSpreadsheetApplicant)
                .toList());

        // Lấy dòng cũ của plan, kiểm tra có ai nhập học thành công (ENROLLED) chưa
        List<AdmissionApplicant> existingApplicants = applicantRepository.findByPlan_IdOrderByRowNumber(planId);
        boolean hasEnrolledApplicants = existingApplicants.stream()
                .anyMatch(applicant -> applicant.getState() == AdmissionApplicantStatus.ENROLLED);

        // validate các AdmissionApplicant đã parse từ file mới
        for (AdmissionApplicant applicant : importedApplicants) {

            applicant.setPlan(plan); // applican này thuộc về plant hiện tại

            if (applicant.getState() == AdmissionApplicantStatus.INVALID) continue;

            List<String> validationErrors = applicantValidationPolicy.validateApplicant(applicant, LocalDate.now());
            if (!validationErrors.isEmpty())
                setApplicantStatus(applicant, AdmissionApplicantStatus.INVALID, String.join("; ", validationErrors));
        }

        // Chỉ giữ lại những thí sinh đã qua bước parse và validate
        var validApplicants = importedApplicants.stream()
                .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.VALID)
                .toList();

        Map<String, StudentAdmission> existingAdmissionsByApplicationCode = new HashMap<>();
        Map<String, List<AdmissionStudentInfo>> existingStudentsByCitizenId = new HashMap<>();

        for (int offset = 0; offset < validApplicants.size(); offset += 500) {
            // Chia nhỏ query để không vượt quá giới hạn tham số của PostgreSQL khi import dữ liệu lớn
            // Tra DB theo lô 500
            List<AdmissionApplicant> applicantBatch =
                    validApplicants.subList(offset, Math.min(offset + 500, validApplicants.size()));

            // Lưu trước các StudentAdmission theo mã hồ sơ để truy vấn, tránh N+1 query
            // dùng để xem hồ sơ này đã được import trước đó chưa ?
            studentAdmissionRepository
                    .findByApplicationCodeIn(applicantBatch.stream()
                            .map(AdmissionApplicant::getApplicationCode)
                            .distinct()
                            .toList())
                    .forEach(existingAdmission -> existingAdmissionsByApplicationCode.put(
                            existingAdmission.getApplicationCode(), existingAdmission));

            //  Lưu trước các StudentAdmission theo CCCD để truy vấn, tránh N+1 query
            // dùng để biết người này đã có trong hệ thống chưa ?
            peopleAdmissionFacade
                    .findStudentsByCitizenIds(applicantBatch.stream()
                            .map(AdmissionApplicant::getCitizenId)
                            .distinct()
                            .toList())
                    .forEach(existingStudent -> existingStudentsByCitizenId
                            .computeIfAbsent(existingStudent.getCitizenId(), citizenId -> new ArrayList<>())
                            .add(existingStudent));
        }

        // phân loại validRows thành: mới, đã tồn tại (EXISTING), hoặc xung đột (CONFLICT)
        Map<String, AdmissionApplicant> firstApplicantByApplicationCode = new HashMap<>();
        Map<String, List<AdmissionApplicant>> applicantsByCitizenId =
                validApplicants.stream().collect(Collectors.groupingBy(AdmissionApplicant::getCitizenId));

        for (AdmissionApplicant applicant : validApplicants) {

            // lấy dữ liệu lên check
            AdmissionApplicant firstApplicantWithSameCode =
                    firstApplicantByApplicationCode.putIfAbsent(applicant.getApplicationCode(), applicant);
            StudentAdmission existingAdmission =
                    existingAdmissionsByApplicationCode.get(applicant.getApplicationCode());
            List<AdmissionStudentInfo> matchingStudents = existingStudentsByCitizenId
                    .getOrDefault(applicant.getCitizenId(), List.of())
                    .stream()
                    .collect(Collectors.toMap(
                            AdmissionStudentInfo::getId, Function.identity(), (first, second) -> first))
                    .values()
                    .stream()
                    .toList();

            // CCCD chưa có trong hệ thống, đây là thí sinh mới hoàn toàn
            // TODO(ADMISSION_IMPORT_VALIDATION_ORDER): Kiểm tra mã hồ sơ trước nhánh CCCD mới;
            //  lỗi có từ trước đợt tách module.
            if (matchingStudents.isEmpty()) continue;

            // Trùng mã hồ sơ ngay trong file
            if (firstApplicantWithSameCode != null) {
                if (identityPolicy.hasSameApplicantData(firstApplicantWithSameCode, applicant)
                        && firstApplicantWithSameCode.getState() != AdmissionApplicantStatus.CONFLICT) {
                    setApplicantStatus(
                            applicant, AdmissionApplicantStatus.EXISTING, "Dòng trùng đã tồn tại trong file");
                } else {
                    setApplicantStatus(
                            firstApplicantWithSameCode,
                            AdmissionApplicantStatus.CONFLICT,
                            "Cùng mã hồ sơ, khác dữ liệu trong file");
                    setApplicantStatus(
                            applicant, AdmissionApplicantStatus.CONFLICT, "Cùng mã hồ sơ, khác dữ liệu trong file");
                }
                continue;
            }

            // Mã hồ sơ đã có trong DB
            if (existingAdmission != null) {
                applicant.setStudentId(existingAdmission.getStudentId());
                applicant.setReuseAccount(true);
                setApplicantStatus(
                        applicant,
                        identityPolicy.hasSameApplicantData(applicant, existingAdmission)
                                ? AdmissionApplicantStatus.EXISTING
                                : AdmissionApplicantStatus.CONFLICT,
                        identityPolicy.hasSameApplicantData(applicant, existingAdmission)
                                ? "Hồ sơ đã tồn tại"
                                : "Cùng mã hồ sơ nhưng khác dữ liệu hoặc CCCD");
                continue;
            }

            // Chưa có mã hồ sơ, nhưng CCCD khớp sinh viên cũ
            if (matchingStudents.size() != 1
                    || matchingStudents.getFirst().isDeleted()
                    || matchingStudents.getFirst().getUserId() == null) {
                setApplicantStatus(
                        applicant, AdmissionApplicantStatus.CONFLICT, "Danh tính cũ đã tồn tại trong hệ thống");
                continue;
            }

            // Khớp đúng một sinh viên, kiểm tra tài khoản
            AdmissionStudentInfo student = matchingStudents.getFirst();
            try {
                var account = identityFacade.getStudentAccount(student.getUserId());
                applicant.setStudentId(student.getId());
                applicant.setReuseAccount(true);

                if (account.locked()) {
                    applicant.setWarningMessage(
                            "Tài khoản cũ đang khóa/ngừng hoạt động; hệ thống giữ nguyên trạng thái");
                }

                if (!TextsNormalizer.normalizeKey(student.getFullName())
                        .equals(TextsNormalizer.normalizeKey(applicant.getFullName()))) {
                    applicant.setWarningMessage("Họ tên khác hồ sơ danh tính cũ; giữ nguyên hồ sơ gốc"
                            + (applicant.getWarningMessage() == null ? "" : "; " + applicant.getWarningMessage()));
                }

            } catch (BaseException exception) {
                setApplicantStatus(
                        applicant,
                        AdmissionApplicantStatus.CONFLICT,
                        "Không thể dùng lại tài khoản cũ; Admin cần kiểm tra");
            }
        }

        // Quét xung đột chéo

        // Một CCCD có nhiều mã hồ sơ
        applicantsByCitizenId.values().stream()
                .filter(group -> group.stream()
                                .map(AdmissionApplicant::getApplicationCode)
                                .distinct()
                                .count()
                        > 1)
                .forEach(group -> group.forEach(applicant -> setApplicantStatus(
                        applicant, AdmissionApplicantStatus.CONFLICT, "Một CCCD có nhiều mã hồ sơ ở cùng đợt phân")));

        // Lan truyền xung đột theo mã hồ sơ
        // Nhóm các dòng theo mã hồ sơ; nếu có ít nhất một dòng CONFLICT thì mọi dòng cùng mã đều thành CONFLICT
        validApplicants.stream()
                .collect(Collectors.groupingBy(AdmissionApplicant::getApplicationCode))
                .values()
                .stream()
                .filter(group ->
                        group.stream().anyMatch(applicant -> applicant.getState() == AdmissionApplicantStatus.CONFLICT))
                .forEach(group -> group.forEach(applicant -> setApplicantStatus(
                        applicant, AdmissionApplicantStatus.CONFLICT, "Cùng mã hồ sơ nhưng có dữ liệu xung đột")));

        // đã có sinh viên nhập học từ lần import trước
        // Khi kế hoạch đã có người nhập học thành công, không được xóa sạch dữ liệu cũ mà phải gộp.
        if (hasEnrolledApplicants) {
            importedApplicants =
                    applicantMergePolicy.mergePreservingEnrolledApplicants(existingApplicants, importedApplicants);
        } else {
            // Xóa toàn bộ dòng import cũ của kế hoạch rồi import lại từ đầu
            applicantRepository.deleteByPlan_Id(planId);
            applicantRepository.flush();
        }

        // Lưu toàn bộ dòng (kể cả INVALID, CONFLICT, để người dùng xem lại).
        applicantRepository.saveAll(importedApplicants);
        applicantRepository.flush();

        // Tổng số sinh viên = số dòng được tính vào đợt
        plan.setTotalStudents((int) importedApplicants.stream()
                .filter(AdmissionApplicant::isIncludedInPlan)
                .count());

        // Trạng thái kế hoạch
        if (hasEnrolledApplicants && importedApplicants.stream().noneMatch(AdmissionApplicant::isPendingEnrollment)) {
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
        AdmissionPlanPolicy.incrementRevision(plan);

        // TODO(AUDIT_LOG): Ghi lần import, hồ sơ bị loại/xung đột và quyết định xử lý lại sau commit.

        log.info(
                "Imported admission candidates: planId={}, rows={}, eligible={}",
                planId,
                importedApplicants.size(),
                plan.getTotalStudents());

        return planQueryService.buildPlanResponse(plan);
    }

    private void setApplicantStatus(
            AdmissionApplicant applicant, AdmissionApplicantStatus status, String errorMessage) {
        applicant.setState(status);
        applicant.setErrorMessage(errorMessage);
    }
}
