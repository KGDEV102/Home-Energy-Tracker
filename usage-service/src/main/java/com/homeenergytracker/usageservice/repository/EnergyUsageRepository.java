package com.homeenergytracker.usageservice.repository;

import com.homeenergytracker.usageservice.event.EnergyUsageEvent;

public interface EnergyUsageRepository {
    void save(EnergyUsageEvent event);
}
