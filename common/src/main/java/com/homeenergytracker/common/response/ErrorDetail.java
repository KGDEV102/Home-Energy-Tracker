package com.homeenergytracker.common.response;

import java.util.Map;

public record ErrorDetail (
        String code,
        String message,
        Map<String,String> details
){
}
