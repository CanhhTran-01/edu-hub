package com.eduhub.edu_hub.people.domain.model;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "faculties")
@Getter
@Setter
public class Faculty extends BaseEntity {
    // TODO(FACULTY_CODE_DATA): Bổ sung mã khoa chính thức cho dữ liệu cũ sau khi được đối soát;
    //  không suy mã từ tên khoa. Khoa chưa có mã không được dùng để cấp MSGV mới.
    @Column(name = "faculty_code", unique = true)
    private String facultyCode;

    @Column(name = "faculty_name", nullable = false)
    private String facultyName;

    @Column(nullable = false)
    private boolean active = true;
}
