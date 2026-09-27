package com.homeenergytracker.deviceservice.dto;

import java.util.List;

public record SliceResponse <T>(
        List<T> items,
        int page,
        int size,
        boolean hasNext
){
}
