package com.homeenergytracker.deviceservice.dto;

import com.homeenergytracker.deviceservice.entity.DeviceModelStatus;
import com.homeenergytracker.deviceservice.entity.DeviceType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

public record DeviceModelDto (

         Long id,

 String manufacturer,

 String modelName,



 DeviceType type,

BigDecimal ratedPowerW,



 DeviceModelStatus status

){
}
