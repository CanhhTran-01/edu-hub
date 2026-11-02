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
import com.eduhub.edu_hub.curriculum.domain.entity.Subject;
import com.eduhub.edu_hub.curriculum.domain.entity.SubjectPrerequisite;
import com.eduhub.edu_hub.curriculum.domain.enums.SubjectStatus;
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

        // check
        if (subjectRepository.existsBySubjectCode(request.getSubjectCode())) {
            throw new BaseException(ErrorCode.DUPLICATE_SUBJECT_CODE);
        }
        if (subjectRepository.existsBySubjectName(request.getSubjectName())) {
            throw new BaseException(ErrorCode.DUPLICATE_SUBJECT_NAME);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // lưu entity
        Subject subject = Subject.builder()
                .subjectCode(request.getSubjectCode())
                .subjectName(request.getSubjectName())
                .credits(request.getCredits())
                .description(request.getDescription())
                .status(SubjectStatus.ACTIVE)
                .build();
        subject.setCreatedAt(now);
        subject.setUpdatedAt(now);

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

        subject.setSubjectName(request.getSubjectName());
        subject.setCredits(request.getCredits());
        subject.setDescription(request.getDescription());
        subject.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

        Subject saved = subjectRepository.save(subject);
        log.info("Cập nhật học phần: id={}, code={}", saved.getId(), saved.getSubjectCode());
        return toResponse(saved);
    }

    @Transactional
    public SubjectResponse deactivateSubject(Long id, SubjectActionRequest request) {
        Subject subject = getSubjectForAction(id, request);
        subject.setStatus(SubjectStatus.INACTIVE);
        subject.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

        Subject saved = subjectRepository.save(subject);
        log.info("Ngừng sử dụng học phần: id={}, code={}, reason={}", id, saved.getSubjectCode(), request.getReason());
        return toResponse(saved);
    }

    @Transactional
    public void deleteSubject(Long id, SubjectActionRequest request) {
        Subject subject = getSubjectForAction(id, request);

        // TODO: Kiểm tra lớp học phần/dữ liệu điểm qua Facade trước khi cho xóa.

        subject.setDeleted(true);
        subject.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        subjectRepository.save(subject);
        log.info("Xóa mềm học phần: id={}, code={}, reason={}", id, subject.getSubjectCode(), request.getReason());
    }

    @Transactional
    public SubjectResponse setPrerequisites(Long id, SetPrerequisiteRequest request) {

        // Khóa đồ thị trước khi đọc để các lần lưu tiên quyết chạy tuần tự.
        List<Subject> subjects = subjectRepository.findAllByDeletedFalseOrderByIdAsc();

        Map<Long, Subject> subjectsById = new LinkedHashMap<>();
        for (Subject subject : subjects) {
            subjectsById.put(subject.getId(), subject);
        }

        Subject subject = subjectsById.get(id);
        if (subject == null) {
            throw new BaseException(ErrorCode.SUBJECT_NOT_FOUND);
        }
        if (subject.getStatus() != SubjectStatus.ACTIVE) {
            throw new BaseException(ErrorCode.SUBJECT_NOT_ACTIVE);
        }

        Set<Long> requestedIds = new LinkedHashSet<>(request.getPrerequisiteSubjectIds());
        if (requestedIds.contains(id)) {
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

        List<SubjectPrerequisite> links = subjectPrerequisiteRepository.findAllByDeletedFalse();
        checkPrerequisiteCycle(subjectsById, links, id, requestedIds);

        // Chỉ sửa Entity sau khi toàn bộ danh sách và đồ thị đã hợp lệ.
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Set<Long> keptIds = new HashSet<>();
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<SubjectPrerequisite> changedLinks = new ArrayList<>();
        for (SubjectPrerequisite link : links) {
            if (link.isDeleted() || !link.getSubject().getId().equals(id)) {
                continue;
            }
            Long prerequisiteId = link.getPrerequisiteSubject().getId();
            if (!requestedIds.contains(prerequisiteId) || !keptIds.add(prerequisiteId)) {
                link.setDeleted(true);
                link.setUpdatedAt(now);
                changedLinks.add(link);
                removed.add(link.getPrerequisiteSubject().getSubjectName());
            }
        }
        for (Long prerequisiteId : requestedIds) {
            if (!keptIds.add(prerequisiteId)) {
                continue;
            }
            Subject prerequisite = subjectsById.get(prerequisiteId);
            SubjectPrerequisite link = SubjectPrerequisite.builder()
                    .subject(subject)
                    .prerequisiteSubject(prerequisite)
                    .build();
            link.setCreatedAt(now);
            link.setUpdatedAt(now);
            changedLinks.add(link);
            added.add(prerequisite.getSubjectCode());
        }

        subject.setUpdatedAt(now);
        subjectPrerequisiteRepository.saveAll(changedLinks);
        Subject saved = subjectRepository.save(subject);
        log.info("Lưu tiên quyết: subjectId={}, added={}, removed={}, requested={}", id, added, removed, requestedIds);
        return toResponse(saved);
    }

    private void checkPrerequisiteCycle(
            Map<Long, Subject> subjectsById, List<SubjectPrerequisite> links, Long id, Set<Long> requestedIds) {
        Map<Long, List<Long>> graph = new LinkedHashMap<>();
        for (Long subjectId : subjectsById.keySet()) {
            graph.put(subjectId, new ArrayList<>());
        }
        for (SubjectPrerequisite link : links) {
            Long subjectId = link.getSubject().getId();
            Long prerequisiteId = link.getPrerequisiteSubject().getId();
            if (!link.isDeleted() && subjectsById.containsKey(subjectId) && subjectsById.containsKey(prerequisiteId)) {
                graph.get(subjectId).add(prerequisiteId);
            }
        }
        graph.put(id, new ArrayList<>(requestedIds));
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
        if (path.contains(id)) {
            List<Long> cycle = new ArrayList<>(path);
            cycle = new ArrayList<>(cycle.subList(cycle.indexOf(id), cycle.size()));
            cycle.add(id);
            return cycle;
        }
        if (!visited.add(id)) {
            return List.of();
        }
        path.add(id);
        for (Long prerequisiteId : graph.getOrDefault(id, List.of())) {
            List<Long> cycle = findCycle(prerequisiteId, graph, visited, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        path.remove(id);
        return List.of();
    }

    @Transactional(readOnly = true)
    public PageResponse<SubjectResponse> searchSubjects(SubjectSearchRequest request) {

        PageRequest pageable = PageRequest.of(request.getPage(), request.getSize(), Sort.by("id"));
        Page<Subject> subjects = subjectRepository.findAll(SubjectSpecification.search(request), pageable);

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
        if (!request.isConfirmed()) {
            throw new BaseException(ErrorCode.SUBJECT_ACTION_NOT_CONFIRMED);
        }
        Subject subject = subjectRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.SUBJECT_NOT_FOUND));

        List<SubjectSimpleResponse> dependents =
                subjectPrerequisiteRepository.findAllByPrerequisiteSubject_IdInAndDeletedFalse(List.of(id)).stream()
                        .filter(link -> !link.isDeleted())
                        .map(SubjectPrerequisite::getSubject)
                        .filter(dependent -> !dependent.isDeleted() && dependent.getStatus() == SubjectStatus.ACTIVE)
                        .distinct()
                        .sorted(Comparator.comparing(Subject::getId))
                        .map(this::toSimpleResponse)
                        .toList();
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
        List<Long> subjectIds = subjects.stream().map(Subject::getId).toList();
        // Lấy quan hệ cho cả trang, tránh truy vấn riêng cho từng học phần.
        Map<Long, List<SubjectPrerequisite>> prerequisitesBySubject =
                subjectPrerequisiteRepository.findAllBySubject_IdInAndDeletedFalse(subjectIds).stream()
                        .collect(Collectors.groupingBy(link -> link.getSubject().getId()));
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
        List<SubjectSimpleResponse> prerequisites = prerequisiteLinks.stream()
                .filter(prerequisite -> !prerequisite.isDeleted())
                .map(SubjectPrerequisite::getPrerequisiteSubject)
                .filter(prerequisite -> !prerequisite.isDeleted())
                .distinct()
                .sorted(Comparator.comparing(Subject::getId))
                .map(this::toSimpleResponse)
                .toList();
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
