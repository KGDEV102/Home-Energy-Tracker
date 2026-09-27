package com.homeenergytracker.deviceservice.exception;
import com.homeenergytracker.common.exception.BusinessException;

public class DeviceNotExist extends BusinessException  {
    public DeviceNotExist() {
        super("DEVICE_NOT_EXIST","Device does not exist");
    }
    public DeviceNotExist(Long userId) {
        super("DEVICE_NOT_EXIST","Devices of user " + userId + " does not exist");
    }
}
