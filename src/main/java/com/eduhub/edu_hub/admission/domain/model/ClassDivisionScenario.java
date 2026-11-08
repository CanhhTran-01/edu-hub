package com.eduhub.edu_hub.admission.domain.model;

import java.util.List;
import lombok.Builder;

@Builder
public record ClassDivisionScenario(
        int classCount, // chia thành mấy lớp ?
        List<Integer> targetSizes, // mỗi lớp bao nhiêu sinh viên ?
        String level, // phân loại theo độ ưu tiên
        int reserveUsed, // sử dụng mấy suất dự phòng ?
        boolean belowMinimum, // có lớp nào dưới mức tối thiểu không ?
        boolean insufficientRooms, // có đủ số phòng đáp ứng kịch bản này không ?
        long suitableRooms, // số phòng phù hợp với kịch bản này
        int requiredRoomCapacity, // sức chứa phòng tối thiểu phòng học cần để đáp ứng kịch bản
        boolean recommended, // kịch bản này có được khuyến nghị không ?
        List<String> warnings) {}
