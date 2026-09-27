package com.homeenergytracker.deviceservice.exception;
import  com.homeenergytracker.common.exception.BusinessException;
import com.homeenergytracker.deviceservice.repository.DeviceModelRepository;

public class DeviceModelNotExist extends  BusinessException {
    public DeviceModelNotExist() {
        super("DEVICE_MODEL_NOT_EXIST","Device model does not exist");
    }
}
