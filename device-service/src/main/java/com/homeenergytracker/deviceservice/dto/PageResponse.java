package com.homeenergytracker.deviceservice.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int currentPage,
        int pageSize,
        int totalPage,
        boolean isFirst,
        boolean isLast,
        boolean hasNext,
        boolean hasPrevious

) {
}
