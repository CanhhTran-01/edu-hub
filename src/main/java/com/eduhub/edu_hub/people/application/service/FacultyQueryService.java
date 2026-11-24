package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.people.api.dto.response.FacultyResponse;
import com.eduhub.edu_hub.people.domain.repository.FacultyRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacultyQueryService {
    private final FacultyRepository facultyRepository;

    public List<FacultyResponse> findFaculties() {
        // Đọc danh mục độc lập với mapping để vẫn chọn/xem khoa khi chưa cấu hình tiền tố.
        return facultyRepository.findAllByDeletedFalseOrderByFacultyNameAscIdAsc().stream()
                .map(faculty -> new FacultyResponse(
                        faculty.getId(), faculty.getFacultyCode(), faculty.getFacultyName(), faculty.isActive()))
                .toList();
    }
}
