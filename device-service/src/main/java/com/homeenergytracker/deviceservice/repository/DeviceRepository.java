package com.homeenergytracker.deviceservice.repository;

import com.homeenergytracker.deviceservice.dto.DeviceResponse;
import com.homeenergytracker.deviceservice.entity.Device;
import com.homeenergytracker.deviceservice.entity.DeviceModel;
import com.homeenergytracker.deviceservice.entity.DeviceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device,Long> {
    Optional<List<Device>> findAllByUserId(Long userId);
    @EntityGraph(attributePaths = "model")
    Optional<Device> findByIdAndUserId(Long id, Long userId);
    @EntityGraph(attributePaths = "model")
    Page<Device>  findAllByUserIdAndStatus(Long userId, DeviceStatus status, Pageable pageable);
    @EntityGraph(attributePaths = "model")
    List<Device> findAllByStatus(DeviceStatus status);


}
