package com.homeenergytracker.usageservice.repository;

import com.homeenergytracker.common.event.EnergyUsageEvent;

public interface EnergyUsageRepository {
    void save(EnergyUsageEvent event);
}
