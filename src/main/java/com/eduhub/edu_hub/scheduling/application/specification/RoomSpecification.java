package com.eduhub.edu_hub.scheduling.application.specification;

import com.eduhub.edu_hub.scheduling.api.dto.RoomSearchRequest;
import com.eduhub.edu_hub.scheduling.domain.model.Room;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class RoomSpecification {
    private RoomSpecification() {}

    public static Specification<Room> search(RoomSearchRequest request) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("deleted")));
            if (StringUtils.hasText(request.getKeyword())) {
                String keyword = "%"
                        + request.getKeyword()
                                .trim()
                                .toUpperCase(Locale.ROOT)
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                        + "%";
                predicates.add(builder.or(
                        builder.like(root.get("roomCode"), keyword, '\\'),
                        builder.like(root.get("buildingCode"), keyword, '\\'),
                        builder.like(builder.upper(root.get("roomName")), keyword, '\\'),
                        builder.like(root.get("campus"), keyword, '\\'),
                        builder.like(root.get("roomNumber"), keyword, '\\')));
            }
            if (StringUtils.hasText(request.getBuildingCode())) {
                predicates.add(builder.equal(
                        root.get("buildingCode"),
                        request.getBuildingCode().trim().toUpperCase(Locale.ROOT)));
            }
            if (request.getStatus() != null) {
                predicates.add(builder.equal(root.get("status"), request.getStatus()));
            }
            if (StringUtils.hasText(request.getCampus())) {
                predicates.add(builder.equal(
                        root.get("campus"), request.getCampus().trim().toUpperCase(Locale.ROOT)));
            }
            if (request.getMinCapacity() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("capacity"), request.getMinCapacity()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
