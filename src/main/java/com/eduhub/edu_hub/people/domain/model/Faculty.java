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
    //  mã danh mục này được giữ cho lịch sử; tiền tố MSGV mới lấy từ mapping theo tên khoa trong SystemConfig.
    @Column(name = "faculty_code", unique = true)
    private String facultyCode;

    @Column(name = "faculty_name", nullable = false)
    private String facultyName;

    @Column(nullable = false)
    private boolean active = true;
}
