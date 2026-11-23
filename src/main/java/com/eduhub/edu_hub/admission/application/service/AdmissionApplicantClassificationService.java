package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.StudentAdmission;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionIdentityPolicy;
import com.eduhub.edu_hub.admission.domain.repository.StudentAdmissionRepository;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionStudentInfo;
import com.eduhub.edu_hub.shared.exception.BaseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Tải danh tính theo lô và điều phối phân loại hồ sơ trong transaction import hiện tại. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AdmissionApplicantClassificationService {
    private static final int IDENTITY_LOOKUP_BATCH_SIZE = 500;

    private final StudentAdmissionRepository studentAdmissionRepository;
    private final PeopleFacade.Admission peopleAdmissionFacade;
    private final IdentityFacade identityFacade;
    private final AdmissionIdentityPolicy identityPolicy;

    public void classifyApplicants(List<AdmissionApplicant> importedApplicants) {
        // Chỉ giữ lại những thí sinh VALID để xử lý tiếp
        var validApplicants = importedApplicants.stream()
                .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.VALID)
                .toList();

        Map<String, StudentAdmission> existingAdmissionsByApplicationCode = new HashMap<>();
        Map<String, List<AdmissionStudentInfo>> existingStudentsByCitizenId = new HashMap<>();

        for (int offset = 0; offset < validApplicants.size(); offset += IDENTITY_LOOKUP_BATCH_SIZE) {
            // Tra DB theo lô 500 để không vượt quá giới hạn tham số của PostgreSQL
            List<AdmissionApplicant> applicantBatch = validApplicants.subList(
                    offset, Math.min(offset + IDENTITY_LOOKUP_BATCH_SIZE, validApplicants.size()));

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
            // NOTE: Nhánh này đọc Student qua facade People; StudentAdmission được tra theo mã hồ sơ ở trên.
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

            // Đối chiếu mã hồ sơ trước nhánh CCCD mới để không bỏ lọt mã trùng trong file hoặc DB.
            identityPolicy.classifyApplicationCode(applicant, firstApplicantWithSameCode, existingAdmission);
            if (applicant.getState() != AdmissionApplicantStatus.VALID) continue;

            matchExistingStudent(applicant, matchingStudents);
        }

        // Giữ cả các dòng vừa đổi trạng thái trong tập đối chiếu để lan truyền đủ xung đột.
        identityPolicy.markCrossApplicantConflicts(validApplicants);
    }

    private void matchExistingStudent(AdmissionApplicant applicant, List<AdmissionStudentInfo> matchingStudents) {
        // CCCD chưa có trong hệ thống, đây là thí sinh mới hoàn toàn
        if (matchingStudents.isEmpty()) return;

        // Chưa có mã hồ sơ, nhưng CCCD khớp sinh viên cũ
        if (matchingStudents.size() != 1
                || matchingStudents.getFirst().isDeleted()
                || matchingStudents.getFirst().getUserId() == null) {
            setApplicantStatus(applicant, AdmissionApplicantStatus.CONFLICT, "Danh tính cũ đã tồn tại trong hệ thống");
            return;
        }

        // Khớp đúng một sinh viên, kiểm tra tài khoản
        AdmissionStudentInfo student = matchingStudents.getFirst();
        try {
            var account = identityFacade.getStudentAccount(student.getUserId());
            identityPolicy.applyReusedStudentAccount(
                    applicant, student.getId(), student.getFullName(), account.locked());
        } catch (BaseException exception) {
            setApplicantStatus(
                    applicant,
                    AdmissionApplicantStatus.CONFLICT,
                    "Không thể dùng lại tài khoản cũ; Admin cần kiểm tra");
        }
    }

    private void setApplicantStatus(
            AdmissionApplicant applicant, AdmissionApplicantStatus status, String errorMessage) {
        applicant.setState(status);
        applicant.setErrorMessage(errorMessage);
    }
}
