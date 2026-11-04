package com.eduhub.edu_hub.curriculum.application;

import com.eduhub.edu_hub.curriculum.api.dto.CreateSubjectRequest;
import com.eduhub.edu_hub.curriculum.api.dto.SetPrerequisiteRequest;
import com.eduhub.edu_hub.curriculum.api.dto.SubjectActionRequest;
import com.eduhub.edu_hub.curriculum.api.dto.SubjectResponse;
import com.eduhub.edu_hub.curriculum.api.dto.SubjectSearchRequest;
import com.eduhub.edu_hub.curriculum.api.dto.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.api.dto.UpdateSubjectRequest;
import com.eduhub.edu_hub.curriculum.application.exception.PrerequisiteCycleException;
import com.eduhub.edu_hub.curriculum.application.exception.SubjectHasDependentsException;
import com.eduhub.edu_hub.curriculum.application.specification.SubjectSpecification;
import com.eduhub.edu_hub.curriculum.domain.enums.SubjectStatus;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.curriculum.domain.model.SubjectPrerequisite;
import com.eduhub.edu_hub.curriculum.domain.repository.SubjectPrerequisiteRepository;
import com.eduhub.edu_hub.curriculum.domain.repository.SubjectRepository;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubjectApplicationService {
    private final SubjectRepository subjectRepository;
    private final SubjectPrerequisiteRepository subjectPrerequisiteRepository;

    @Transactional
    public SubjectResponse createSubject(CreateSubjectRequest request) {

        // checking
        if (subjectRepository.existsBySubjectCode(request.getSubjectCode())) {
            throw new BaseException(ErrorCode.DUPLICATE_SUBJECT_CODE);
        }
        if (subjectRepository.existsBySubjectName(request.getSubjectName())) {
            throw new BaseException(ErrorCode.DUPLICATE_SUBJECT_NAME);
        }

        // TODO: cần thống nhất về lại 1 múi giờ
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // update field
        Subject subject = Subject.builder()
                .subjectCode(request.getSubjectCode())
                .subjectName(request.getSubjectName())
                .credits(request.getCredits())
                .description(request.getDescription())
                .status(SubjectStatus.ACTIVE)
                .build();
        subject.setCreatedAt(now);
        subject.setUpdatedAt(now);

        // lưu lại entity
        Subject saved = subjectRepository.save(subject);
        log.info("Lưu học phần: id={}, code={}", saved.getId(), saved.getSubjectCode());

        return SubjectResponse.builder()
                .id(saved.getId())
                .subjectCode(saved.getSubjectCode())
                .subjectName(saved.getSubjectName())
                .credits(saved.getCredits())
                .description(saved.getDescription())
                .status(saved.getStatus())
                .prerequisites(List.of())
                .requiredBy(List.of())
                .build();
    }

    @Transactional
    public SubjectResponse updateSubject(Long id, UpdateSubjectRequest request) {

        Subject subject = subjectRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.SUBJECT_NOT_FOUND));

        // checking
        if (subject.getStatus() != SubjectStatus.ACTIVE) {
            throw new BaseException(ErrorCode.SUBJECT_NOT_ACTIVE);
        }
        if (request.getSubjectCode() != null && !request.getSubjectCode().equals(subject.getSubjectCode())) {
            throw new BaseException(ErrorCode.SUBJECT_CODE_IMMUTABLE);
        }
        if (subjectRepository.existsBySubjectNameAndIdNot(request.getSubjectName(), id)) {
            throw new BaseException(ErrorCode.DUPLICATE_SUBJECT_NAME);
        }
        // TODO: Kiểm tra điểm và lớp đang mở qua Facade khi enrollment/scheduling sẵn sàng.

        // update field
        subject.setSubjectName(request.getSubjectName());
        subject.setCredits(request.getCredits());
        subject.setDescription(request.getDescription());
        subject.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

        // lưu kết quả
        Subject saved = subjectRepository.save(subject);
        log.info("Cập nhật học phần: id={}, code={}", saved.getId(), saved.getSubjectCode());

        return toResponse(saved);
    }

    @Transactional
    public SubjectResponse deactivateSubject(Long id, SubjectActionRequest request) {

        // check xem môn này có môn nào cần nó làm tiên quyết không ?
        Subject subject = getSubjectForAction(id, request);

        // deactivate
        subject.setStatus(SubjectStatus.INACTIVE);
        subject.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

        Subject saved = subjectRepository.save(subject);
        log.info("Ngừng sử dụng học phần: id={}, code={}, reason={}", id, saved.getSubjectCode(), request.getReason());

        return toResponse(saved);
    }

    @Transactional
    public void deleteSubject(Long id, SubjectActionRequest request) {

        // check xem môn này có môn nào cần nó làm tiên quyết không ?
        Subject subject = getSubjectForAction(id, request);

        // TODO: Kiểm tra lớp học phần/dữ liệu điểm qua Facade trước khi cho xóa.

        // delete học phần - chỉnh cờ isDeleted
        subject.setDeleted(true);
        subject.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

        subjectRepository.save(subject);
        log.info("Xóa mềm học phần: id={}, code={}, reason={}", id, subject.getSubjectCode(), request.getReason());
    }

    @Transactional
    public SubjectResponse setPrerequisites(Long id, SetPrerequisiteRequest request) {

        // Lock lại đồ thị (lock toàn bộ rows) trước khi đọc - vấn đề đa luồng
        // ORDER BY id ASC để tránh deadlock khi nhiều transaction cùng khóa nhiều row theo thứ tự khác nhau
        List<Subject> subjects = subjectRepository.findAllByDeletedFalseOrderByIdAsc();

        // lôi hết subjects ra nhét vào một map gồm đi kèm với id của nó làm key để tiện truy vấn
        Map<Long, Subject> subjectsById = new LinkedHashMap<>();
        for (Subject subject : subjects) {
            subjectsById.put(subject.getId(), subject);
        }

        // lôi subject mà client cần lập/gỡ môn tiên quyết cho nó ra
        Subject subject = subjectsById.get(id);
        if (subject == null) {
            throw new BaseException(ErrorCode.SUBJECT_NOT_FOUND);
        }
        if (subject.getStatus() != SubjectStatus.ACTIVE) {
            throw new BaseException(ErrorCode.SUBJECT_NOT_ACTIVE);
        }

        // lôi các môn tiên quyết cần lập do request gửi về ra
        Set<Long> requestedIds = new LinkedHashSet<>(request.getPrerequisiteSubjectIds());

        // checking các môn tiên quyết cần lập
        if (requestedIds.contains(id)) {
            // không cho phép 1 môn làm tiên quyết của chính mình
            throw new BaseException(ErrorCode.PREREQUISITE_SELF_REFERENCE);
        }
        for (Long prerequisiteId : requestedIds) {
            Subject prerequisite = subjectsById.get(prerequisiteId);
            if (prerequisite == null) {
                throw new BaseException(ErrorCode.SUBJECT_NOT_FOUND);
            }
            if (prerequisite.getStatus() != SubjectStatus.ACTIVE) {
                throw new BaseException(ErrorCode.PREREQUISITE_NOT_ACTIVE);
            }
        }

        // lấy toàn bộ bản ghi tiên quyết check chu trình
        List<SubjectPrerequisite> links = subjectPrerequisiteRepository.findAllByDeletedFalse();

        // DFS check chu trình với 4 input:
        // DS môn học, toàn bộ bản ghi tiên quyết trong database, môn cần lập tiên quyết, ds môn tiên quyết
        checkPrerequisiteCycle(subjectsById, links, id, requestedIds);

        // Sửa Entity sau khi toàn bộ danh sách và đồ thị đã hợp lệ.
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Set<Long> keptIds = new HashSet<>(); // các môn tiên quyết đã có liên kết hợp lệ sau khi xử lý
        List<String> added = new ArrayList<>(); // các môn các liên kết mới thêm, phục vụ log
        List<String> removed = new ArrayList<>(); // các môn của các liên kết bị gỡ, phục vụ log
        List<SubjectPrerequisite> changedLinks = new ArrayList<>(); // tất cả liên kết cần ghi xuống DB

        // loop xử lý "liên kết cũ nào bị gỡ (xóa mềm) và liên kết nào được giữ"
        for (SubjectPrerequisite link : links) {
            // bỏ qua bản ghi bị xóa mềm và bản ghi không phải môn cần thiết lập tiên quyết
            if (link.isDeleted() || !link.getSubject().getId().equals(id)) {
                continue;
            }

            // Lấy ID của môn tiên quyết mà liên kết cũ này đang trỏ tới
            Long prerequisiteId = link.getPrerequisiteSubject().getId();

            // môn tiên quyết này không còn nằm trong request, tức client muốn gỡ nó
            // ID đã có sẵn trong keptIds chưa, tức liên kết này là bản trùng, bị gỡ.
            if (!requestedIds.contains(prerequisiteId) || !keptIds.add(prerequisiteId)) {
                link.setDeleted(true);
                link.setUpdatedAt(now);
                changedLinks.add(link);
                removed.add(link.getPrerequisiteSubject().getSubjectName());
            }
        }

        // loop xử lý: "thêm liên kết mới, chưa có thì tạo mới, có rồi thì bỏ qua"
        for (Long prerequisiteId : requestedIds) {

            // keptIds.add trả về false khi ID đã có trong keptIds
            if (!keptIds.add(prerequisiteId)) {
                continue;
            }

            // thêm mới liên kết
            Subject prerequisite = subjectsById.get(prerequisiteId);
            SubjectPrerequisite link = SubjectPrerequisite.builder()
                    .subject(subject)
                    .prerequisiteSubject(prerequisite)
                    .build();
            link.setCreatedAt(now);
            link.setUpdatedAt(now);

            // lưu lại
            changedLinks.add(link);
            added.add(prerequisite.getSubjectCode());
        }

        // ghi xuống DB
        subject.setUpdatedAt(now);
        subjectPrerequisiteRepository.saveAll(changedLinks);
        Subject saved = subjectRepository.save(subject);
        log.info("Lưu tiên quyết: subjectId={}, added={}, removed={}, requested={}", id, added, removed, requestedIds);

        return toResponse(saved);
    }

    private void checkPrerequisiteCycle(
            Map<Long, Subject> subjectsById, List<SubjectPrerequisite> links, Long id, Set<Long> requestedIds) {

        // B1: tạo graph rỗng. Mỗi môn học là một đỉnh, danh sách kề ban đầu rỗng
        Map<Long, List<Long>> graph = new LinkedHashMap<>();
        for (Long subjectId : subjectsById.keySet()) {
            graph.put(subjectId, new ArrayList<>());
        }

        // B2: từ DB record, thêm cạnh cho graph. Với mỗi liên kết còn sống, thêm cạnh "môn --> môn tiên quyết của nó"
        for (SubjectPrerequisite link : links) {
            Long subjectId = link.getSubject().getId(); // lấy subject lên
            Long prerequisiteId = link.getPrerequisiteSubject().getId(); // lấy prerequisite của subject đó
            if (!link.isDeleted() && subjectsById.containsKey(subjectId) && subjectsById.containsKey(prerequisiteId)) {
                // bản ghi tiên quyết, subject và prerequisiteSubject phải active
                graph.get(subjectId).add(prerequisiteId);
            }
        }

        // B3: ghi đè cạnh của môn cần lập tiên quyết bằng danh sách môn tiên quyết từ request
        graph.put(id, new ArrayList<>(requestedIds));

        // B4: chạy DFS từ mọi đỉnh
        Set<Long> visited = new HashSet<>();
        for (Long subjectId : graph.keySet()) {
            List<Long> cycle = findCycle(subjectId, graph, visited, new LinkedHashSet<>());
            if (!cycle.isEmpty()) {
                throw new PrerequisiteCycleException(cycle.stream()
                        .map(subjectsById::get)
                        .map(this::toSimpleResponse)
                        .toList());
            }
        }
    }

    private List<Long> findCycle(Long id, Map<Long, List<Long>> graph, Set<Long> visited, LinkedHashSet<Long> path) {

        // Gặp lại một đỉnh đang nằm trên đường đi nghĩa là có chu trình
        if (path.contains(id)) {
            List<Long> cycle = new ArrayList<>(path);
            cycle = new ArrayList<>(cycle.subList(cycle.indexOf(id), cycle.size()));
            cycle.add(id); // thêm id vào cuối để khép vòng, ví dụ [A, B, C, A]
            return cycle;
        }

        // Đỉnh đã thăm xong ở nhánh khác và không dẫn tới chu trình thì bỏ qua
        if (!visited.add(id)) {
            return List.of();
        }

        path.add(id);

        // Thêm đỉnh vào đường đi, đệ quy vào từng môn tiên quyết
        // nhánh nào trả về chu trình thì trả ngược lên
        for (Long prerequisiteId : graph.getOrDefault(id, List.of())) {
            List<Long> cycle = findCycle(prerequisiteId, graph, visited, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }

        // duyệt xong mà không thấy thì gỡ đỉnh khỏi path (backtrack) và trả về rỗng
        path.remove(id);
        return List.of();
    }

    @Transactional(readOnly = true)
    public PageResponse<SubjectResponse> searchSubjects(SubjectSearchRequest request) {

        // query phân trang theo tiêu chí với specification
        PageRequest pageable = PageRequest.of(request.getPage(), request.getSize(), Sort.by("id"));
        Page<Subject> subjects = subjectRepository.findAll(SubjectSpecification.search(request), pageable);

        // đổi sang response
        List<SubjectResponse> responses = toResponseList(subjects.getContent());

        return PageMapper.toPageResponse(new PageImpl<>(responses, pageable, subjects.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public SubjectResponse getSubjectById(Long id) {

        Subject subject = subjectRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.SUBJECT_NOT_FOUND));

        return toResponse(subject);
    }

    private Subject getSubjectForAction(Long id, SubjectActionRequest request) {

        // cờ xác nhận thao tác từ client gửi về
        if (!request.isConfirmed()) {
            throw new BaseException(ErrorCode.SUBJECT_ACTION_NOT_CONFIRMED);
        }

        // lôi subject lên
        Subject subject = subjectRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.SUBJECT_NOT_FOUND));

        // lấy danh sách các môn các môn cần môn vừa lôi lên làm tiên quyết
        List<SubjectSimpleResponse> dependents =
                subjectPrerequisiteRepository.findAllByPrerequisiteSubject_IdInAndDeletedFalse(List.of(id)).stream()
                        .filter(link -> !link.isDeleted())
                        .map(SubjectPrerequisite::getSubject)
                        .filter(dependent -> !dependent.isDeleted() && dependent.getStatus() == SubjectStatus.ACTIVE)
                        .distinct()
                        .sorted(Comparator.comparing(Subject::getId))
                        .map(this::toSimpleResponse)
                        .toList();

        // nhả lỗi nếu có môn phụ thuộc (không cho deactivate subject vừa lôi lên)
        if (!dependents.isEmpty()) {
            throw new SubjectHasDependentsException(dependents);
        }

        return subject;
    }

    private SubjectResponse toResponse(Subject subject) {
        return toResponseList(List.of(subject)).getFirst();
    }

    private List<SubjectResponse> toResponseList(List<Subject> subjects) {

        if (subjects.isEmpty()) {
            return List.of();
        }

        // lấy các id trong list subjects
        List<Long> subjectIds = subjects.stream().map(Subject::getId).toList();

        // từ subject_ids, lấy các môn là tiên quyết của môn khác, gom theo ID subject
        // "môn này cần học trước những môn nào ?"
        // key = subject_id, value = các môn làm tiên quyết của key
        Map<Long, List<SubjectPrerequisite>> prerequisitesBySubject =
                subjectPrerequisiteRepository.findAllBySubject_IdInAndDeletedFalse(subjectIds).stream()
                        .collect(Collectors.groupingBy(link -> link.getSubject().getId()));

        // từ subject_ids, lấy các môn được môn được môn khác yêu cầu làm tiên quyết, gom theo ID prerequisiteSubject
        // “môn này là tiên quyết của những môn nào ?”
        // key = prerequisite_subject_id, value = các môn cần key làm tiên quyết cho nó
        Map<Long, List<SubjectPrerequisite>> requiredBySubject =
                subjectPrerequisiteRepository.findAllByPrerequisiteSubject_IdInAndDeletedFalse(subjectIds).stream()
                        .collect(Collectors.groupingBy(
                                link -> link.getPrerequisiteSubject().getId()));

        return subjects.stream()
                .map(subject -> toResponse(
                        subject,
                        prerequisitesBySubject.getOrDefault(subject.getId(), List.of()),
                        requiredBySubject.getOrDefault(subject.getId(), List.of())))
                .toList();
    }

    private SubjectResponse toResponse(
            Subject subject, List<SubjectPrerequisite> prerequisiteLinks, List<SubjectPrerequisite> dependentLinks) {

        // convert sang response cho danh sách môn tiên quyết của môn hiện tại
        List<SubjectSimpleResponse> prerequisites = prerequisiteLinks.stream()
                .filter(prerequisite -> !prerequisite.isDeleted())
                .map(SubjectPrerequisite::getPrerequisiteSubject)
                .filter(prerequisite -> !prerequisite.isDeleted())
                .distinct()
                .sorted(Comparator.comparing(Subject::getId))
                .map(this::toSimpleResponse)
                .toList();

        // convert sang response cho danh sách môn là cần môn hiện tại làm tiên quyết
        List<SubjectSimpleResponse> requiredBy = dependentLinks.stream()
                .filter(prerequisite -> !prerequisite.isDeleted())
                .map(SubjectPrerequisite::getSubject)
                .filter(dependent -> !dependent.isDeleted())
                .distinct()
                .sorted(Comparator.comparing(Subject::getId))
                .map(this::toSimpleResponse)
                .toList();

        return SubjectResponse.builder()
                .id(subject.getId())
                .subjectCode(subject.getSubjectCode())
                .subjectName(subject.getSubjectName())
                .credits(subject.getCredits())
                .description(subject.getDescription())
                .status(subject.getStatus())
                .prerequisites(prerequisites)
                .requiredBy(requiredBy)
                .build();
    }

    private SubjectSimpleResponse toSimpleResponse(Subject subject) {
        return SubjectSimpleResponse.builder()
                .id(subject.getId())
                .subjectCode(subject.getSubjectCode())
                .subjectName(subject.getSubjectName())
                .credits(subject.getCredits())
                .build();
    }
}
