package com.eduhub.edu_hub.curriculum.application.service;

import com.eduhub.edu_hub.curriculum.api.dto.response.MajorResponse;
import com.eduhub.edu_hub.curriculum.domain.repository.MajorRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MajorQueryService {
    private final MajorRepository majorRepository;

    public List<MajorResponse> findMajors() {
        // Ngành ngừng hoạt động vẫn cần để tìm phương án cũ; không tải quan hệ Faculty cho dropdown.
        return majorRepository.findAllByDeletedFalseOrderByMajorCodeAscIdAsc().stream()
                .map(major ->
                        new MajorResponse(major.getId(), major.getMajorCode(), major.getMajorName(), major.getStatus()))
                .toList();
    }
}
