package com.homeenergytracker.userservice.exception;
import com.homeenergytracker.common.exception.BusinessException;

public class UserNotExists extends  BusinessException{
    public UserNotExists(){
        super("USER_NOT_EXISTS","User not exists");
    }
}
