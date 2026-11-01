package com.eduhub.edu_hub.shared.mapper;

import com.eduhub.edu_hub.shared.common.PageResponse;
import org.springframework.data.domain.Page;

public class PageMapper {
    public static <T> PageResponse<T> toPageResponse(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isLast(page.isLast())
                .build();
    }
}
