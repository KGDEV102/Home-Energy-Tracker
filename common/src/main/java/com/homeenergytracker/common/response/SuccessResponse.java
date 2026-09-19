package com.homeenergytracker.common.response;

public record SuccessResponse<T>(
        boolean success,
        T data,
        String message
) {
    public static <T> SuccessResponse<T> of(T data, String message){
        return new SuccessResponse<T>(true, data, message);
    }
    public static <T> SuccessResponse<T> of(T data){
        return new SuccessResponse<T>(true, data, "Operation Successful");
    }
    public static <T> SuccessResponse<T> of(){
        return new SuccessResponse<T>(true, null, "Operation Successful");
    }
}
