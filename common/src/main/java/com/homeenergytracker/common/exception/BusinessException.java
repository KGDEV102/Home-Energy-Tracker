package com.homeenergytracker.common.exception;

public class BusinessException extends RuntimeException {
    private final String code;
    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String getCode() {
        return code;
    }
}
