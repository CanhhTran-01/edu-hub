package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.LecturerTeachingSubject;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LecturerTeachingSubjectRepository extends JpaRepository<LecturerTeachingSubject, Long> {
    // Đọc cả liên kết đã xóa để chọn lại sẽ khôi phục đúng bản ghi, không tạo trùng unique giảng viên–môn.
    List<LecturerTeachingSubject> findByLecturerIdOrderBySubjectIdAsc(Long lecturerId);

    List<LecturerTeachingSubject> findByLecturerIdInAndDeletedFalseOrderByLecturerIdAscSubjectIdAsc(
            Collection<Long> lecturerIds);
}
