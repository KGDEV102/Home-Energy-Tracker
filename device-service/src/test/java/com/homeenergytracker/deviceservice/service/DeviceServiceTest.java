package com.homeenergytracker.deviceservice.service;

import com.homeenergytracker.deviceservice.dto.CreateDevice;
import com.homeenergytracker.deviceservice.dto.DeviceResponse;
import com.homeenergytracker.deviceservice.dto.PageResponse;
import com.homeenergytracker.deviceservice.dto.UpdateDevice;
import com.homeenergytracker.deviceservice.entity.*;
import com.homeenergytracker.deviceservice.exception.DeviceModelNotExist;
import com.homeenergytracker.deviceservice.exception.DeviceNotExist;
import com.homeenergytracker.deviceservice.exception.InvalidDeviceRequestException;
import com.homeenergytracker.deviceservice.mapper.DeviceMapper;
import com.homeenergytracker.deviceservice.repository.DeviceModelRepository;
import com.homeenergytracker.deviceservice.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {
    @Mock
    DeviceRepository deviceRepository;
    @Mock
    DeviceModelRepository deviceModelRepository;
    @Mock
    DeviceMapper deviceMapper;
    @InjectMocks
    DeviceService deviceService;

    @Test
    void createDevice_shouldCreateCatalogDevice(){
        DeviceModel model = new DeviceModel();
        model.setId(1L);
        model.setType(DeviceType.AIR_CONDITIONER);
        model.setStatus(DeviceModelStatus.ACTIVE);
        CreateDevice request = new CreateDevice(
                null,
                null,
                null,
                1L,
                10L,
                "Living room AC",
                null,
                "Living room",
                DeviceStatus.ACTIVE
        );
        DeviceResponse mappedResponse = mock(DeviceResponse.class);
        when(deviceModelRepository.findById(1L))
                .thenReturn(Optional.of(model));

        when(deviceMapper.toDeviceResponse(any(Device.class)))
                .thenReturn(mappedResponse);

        DeviceResponse result = deviceService.createDevice(10L, request);
        ArgumentCaptor<Device> captor =
                ArgumentCaptor.forClass(Device.class);

        verify(deviceRepository).save(captor.capture());

        Device savedDevice = captor.getValue();

        assertSame(model, savedDevice.getModel());
        assertEquals(
                DeviceType.AIR_CONDITIONER,
                savedDevice.getType()
        );
        assertNull(savedDevice.getCustomManufacturer());
        assertNull(savedDevice.getCustomModelName());
        assertEquals(10L, savedDevice.getUserId());
        assertEquals("Living room AC", savedDevice.getName());
        assertEquals("Living room", savedDevice.getLocation());
        assertEquals(DeviceStatus.ACTIVE, savedDevice.getStatus());
        assertSame(mappedResponse, result);

        verify(deviceMapper).toDeviceResponse(savedDevice);

    }

    @Test
    void createDevice_shouldCreateCustomDevice() {
        CreateDevice request = new CreateDevice(
                "Custom manufacturer",
                "Custom model",
                BigDecimal.valueOf(100),
                null,
                10L,
                "Bedroom device",
                DeviceType.OTHER,
                "Bedroom",
                DeviceStatus.ACTIVE
        );

        DeviceResponse mappedResponse = mock(DeviceResponse.class);
        when(deviceMapper.toDeviceResponse(any(Device.class)))
                .thenReturn(mappedResponse);

        DeviceResponse result = deviceService.createDevice(10L, request);

        ArgumentCaptor<Device> captor =
                ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());

        Device savedDevice = captor.getValue();

        assertNull(savedDevice.getModel());
        assertEquals("Custom manufacturer", savedDevice.getCustomManufacturer());
        assertEquals("Custom model", savedDevice.getCustomModelName());
        assertEquals(BigDecimal.valueOf(100), savedDevice.getCustomRatedPowerW());
        assertEquals(DeviceType.OTHER, savedDevice.getType());
        assertEquals(10L, savedDevice.getUserId());
        assertEquals("Bedroom device", savedDevice.getName());
        assertEquals("Bedroom", savedDevice.getLocation());
        assertEquals(DeviceStatus.ACTIVE, savedDevice.getStatus());
        assertSame(mappedResponse, result);

        verifyNoInteractions(deviceModelRepository);
        verify(deviceMapper).toDeviceResponse(savedDevice);
    }

    @Test
    void createDevice_shouldAllowNullCustomManufacturer() {
        CreateDevice request = new CreateDevice(
                null,
                "Custom model",
                BigDecimal.valueOf(100),
                null,
                10L,
                "Custom device",
                DeviceType.OTHER,
                "Bedroom",
                DeviceStatus.ACTIVE
        );

        deviceService.createDevice(10L, request);

        ArgumentCaptor<Device> captor =
                ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());

        Device savedDevice = captor.getValue();

        assertNull(savedDevice.getModel());
        assertNull(savedDevice.getCustomManufacturer());
        assertEquals("Custom model", savedDevice.getCustomModelName());
        assertEquals(DeviceType.OTHER, savedDevice.getType());

        verifyNoInteractions(deviceModelRepository);
        verify(deviceMapper).toDeviceResponse(savedDevice);
    }

    @Test
    void createDevice_shouldThrowWhenModelDoesNotExist() {
        CreateDevice request = new CreateDevice(
                null,
                null,
                null,
                999L,
                10L,
                "Unknown catalog device",
                null,
                "Living room",
                DeviceStatus.ACTIVE
        );

        when(deviceModelRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                DeviceModelNotExist.class,
                () -> deviceService.createDevice(10L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
        verify(deviceMapper, never()).toDeviceResponse(any(Device.class));
    }

    @Test
    void createDevice_shouldThrowWhenModelIsNotActive() {
        DeviceModel model = new DeviceModel();
        model.setId(1L);
        model.setType(DeviceType.AIR_CONDITIONER);
        model.setStatus(DeviceModelStatus.DISCONTINUED);

        CreateDevice request = new CreateDevice(
                null,
                null,
                null,
                1L,
                10L,
                "Discontinued catalog device",
                null,
                "Living room",
                DeviceStatus.ACTIVE
        );

        when(deviceModelRepository.findById(1L))
                .thenReturn(Optional.of(model));

        assertThrows(
                InvalidDeviceRequestException.class,
                () -> deviceService.createDevice(10L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
        verify(deviceMapper, never()).toDeviceResponse(any(Device.class));
    }

    @Test
    void createDevice_shouldThrowWhenCatalogHasCustomManufacturer() {
        DeviceModel model = new DeviceModel();
        model.setId(1L);
        model.setType(DeviceType.AIR_CONDITIONER);
        model.setStatus(DeviceModelStatus.ACTIVE);

        CreateDevice request = new CreateDevice(
                "Custom manufacturer",
                null,
                null,
                1L,
                10L,
                "Catalog device",
                null,
                "Living room",
                DeviceStatus.ACTIVE
        );

        when(deviceModelRepository.findById(1L))
                .thenReturn(Optional.of(model));

        assertThrows(
                InvalidDeviceRequestException.class,
                () -> deviceService.createDevice(10L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    void createDevice_shouldThrowWhenCatalogHasCustomModelName() {
        DeviceModel model = new DeviceModel();
        model.setId(1L);
        model.setType(DeviceType.AIR_CONDITIONER);
        model.setStatus(DeviceModelStatus.ACTIVE);

        CreateDevice request = new CreateDevice(
                null,
                "Custom model",
                null,
                1L,
                10L,
                "Catalog device",
                null,
                "Living room",
                DeviceStatus.ACTIVE
        );

        when(deviceModelRepository.findById(1L))
                .thenReturn(Optional.of(model));

        assertThrows(
                InvalidDeviceRequestException.class,
                () -> deviceService.createDevice(10L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    void createDevice_shouldThrowWhenCatalogHasType() {
        DeviceModel model = new DeviceModel();
        model.setId(1L);
        model.setType(DeviceType.AIR_CONDITIONER);
        model.setStatus(DeviceModelStatus.ACTIVE);

        CreateDevice request = new CreateDevice(
                null,
                null,
                null,
                1L,
                10L,
                "Catalog device",
                DeviceType.OTHER,
                "Living room",
                DeviceStatus.ACTIVE
        );

        when(deviceModelRepository.findById(1L))
                .thenReturn(Optional.of(model));

        assertThrows(
                InvalidDeviceRequestException.class,
                () -> deviceService.createDevice(10L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    void createDevice_shouldThrowWhenCustomModelNameIsNull() {
        CreateDevice request = new CreateDevice(
                "Custom manufacturer",
                null,
                BigDecimal.valueOf(100),
                null,
                10L,
                "Custom device",
                DeviceType.OTHER,
                "Bedroom",
                DeviceStatus.ACTIVE
        );

        assertThrows(
                InvalidDeviceRequestException.class,
                () -> deviceService.createDevice(10L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
        verifyNoInteractions(deviceModelRepository);
    }

    @Test
    void createDevice_shouldThrowWhenCustomModelNameIsBlank() {
        CreateDevice request = new CreateDevice(
                "Custom manufacturer",
                "   ",
                BigDecimal.valueOf(100),
                null,
                10L,
                "Custom device",
                DeviceType.OTHER,
                "Bedroom",
                DeviceStatus.ACTIVE
        );

        assertThrows(
                InvalidDeviceRequestException.class,
                () -> deviceService.createDevice(10L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
        verifyNoInteractions(deviceModelRepository);
    }

    @Test
    void createDevice_shouldThrowWhenCustomTypeIsNull() {
        CreateDevice request = new CreateDevice(
                "Custom manufacturer",
                "Custom model",
                BigDecimal.valueOf(100),
                null,
                10L,
                "Custom device",
                null,
                "Bedroom",
                DeviceStatus.ACTIVE
        );

        assertThrows(
                InvalidDeviceRequestException.class,
                () -> deviceService.createDevice(10L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
        verifyNoInteractions(deviceModelRepository);

    }

    @Test
    void createDevice_shouldThrowWhenCustomRatedPowerIsNull() {
        CreateDevice request = new CreateDevice(
                "Custom manufacturer",
                "Custom model",
                null,
                null,
                10L,
                "Custom device",
                DeviceType.OTHER,
                "Bedroom",
                DeviceStatus.ACTIVE
        );

        assertThrows(
                InvalidDeviceRequestException.class,
                () -> deviceService.createDevice(10L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
        verifyNoInteractions(deviceModelRepository);
    }

    @Test
    void createDevice_shouldThrowWhenCatalogHasCustomRatedPower() {
        DeviceModel model = new DeviceModel();
        model.setId(1L);
        model.setType(DeviceType.AIR_CONDITIONER);
        model.setStatus(DeviceModelStatus.ACTIVE);

        CreateDevice request = new CreateDevice(
                null,
                null,
                BigDecimal.valueOf(1200),
                1L,
                10L,
                "Catalog device",
                null,
                "Living room",
                DeviceStatus.ACTIVE
        );

        when(deviceModelRepository.findById(1L))
                .thenReturn(Optional.of(model));

        assertThrows(
                InvalidDeviceRequestException.class,
                () -> deviceService.createDevice(10L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    void updateDevice_shouldUpdateCommonFields() {
        Device device = new Device();
        device.setId(1L);
        device.setModel(new DeviceModel());

        UpdateDevice request = new UpdateDevice(
                null, null, null, null, "Updated name", null,
                "Updated location", DeviceStatus.DISABLED
        );
        DeviceResponse response = mock(DeviceResponse.class);

        when(deviceRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(device));
        when(deviceMapper.toDeviceResponse(device)).thenReturn(response);

        DeviceResponse result = deviceService.updateDevice(10L, 1L, request);

        assertSame(response, result);
        verify(deviceMapper).updateEntity(request, device);
        verify(deviceRepository).save(device);
        verify(deviceMapper).toDeviceResponse(device);
    }

    @Test
    void updateDevice_shouldUpdateCustomDevice() {
        Device device = new Device();
        device.setId(1L);
        device.setCustomModelName("Old model");
        device.setCustomRatedPowerW(BigDecimal.valueOf(50));
        device.setType(DeviceType.OTHER);

        UpdateDevice request = new UpdateDevice(
                "New manufacturer", "New model", BigDecimal.valueOf(75), null, null,
                DeviceType.CAMERA, null, null
        );
        DeviceResponse response = mock(DeviceResponse.class);

        when(deviceRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(device));
        when(deviceMapper.toDeviceResponse(device)).thenReturn(response);

        DeviceResponse result = deviceService.updateDevice(10L, 1L, request);

        assertEquals("New manufacturer", device.getCustomManufacturer());
        assertEquals("New model", device.getCustomModelName());
        assertEquals(BigDecimal.valueOf(75), device.getCustomRatedPowerW());
        assertEquals(DeviceType.CAMERA, device.getType());
        assertSame(response, result);
        verify(deviceMapper).updateEntity(request, device);
        verify(deviceRepository).save(device);
    }

    @Test
    void updateDevice_shouldSwitchCustomDeviceToCatalog() {
        Device device = new Device();
        device.setId(1L);
        device.setCustomManufacturer("Old manufacturer");
        device.setCustomModelName("Old model");
        device.setCustomRatedPowerW(BigDecimal.valueOf(100));
        device.setType(DeviceType.OTHER);

        DeviceModel model = new DeviceModel();
        model.setId(2L);
        model.setType(DeviceType.AIR_CONDITIONER);
        model.setStatus(DeviceModelStatus.ACTIVE);

        UpdateDevice request = new UpdateDevice(
                null, null, null, 2L, null, null, null, null
        );

        when(deviceRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(device));
        when(deviceModelRepository.findById(2L)).thenReturn(Optional.of(model));

        deviceService.updateDevice(10L, 1L, request);

        assertSame(model, device.getModel());
        assertEquals(DeviceType.AIR_CONDITIONER, device.getType());
        assertNull(device.getCustomManufacturer());
        assertNull(device.getCustomModelName());
        assertNull(device.getCustomRatedPowerW());
        verify(deviceRepository).save(device);
    }

    @Test
    void updateDevice_shouldThrowWhenDeviceDoesNotExist() {
        UpdateDevice request = new UpdateDevice(
                null, null, null, null, "Name", null, null, null
        );

        when(deviceRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.empty());

        assertThrows(
                DeviceNotExist.class,
                () -> deviceService.updateDevice(10L, 1L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
        verifyNoInteractions(deviceMapper);
    }

    @Test
    void updateDevice_shouldThrowWhenModelDoesNotExist() {
        Device device = new Device();
        device.setId(1L);
        when(deviceRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(device));
        when(deviceModelRepository.findById(2L)).thenReturn(Optional.empty());

        UpdateDevice request = new UpdateDevice(
                null, null, null, 2L, null, null, null, null
        );

        assertThrows(
                DeviceModelNotExist.class,
                () -> deviceService.updateDevice(10L, 1L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    void updateDevice_shouldThrowWhenModelIsNotActive() {
        Device device = new Device();
        device.setId(1L);

        DeviceModel model = new DeviceModel();
        model.setId(2L);
        model.setType(DeviceType.CAMERA);
        model.setStatus(DeviceModelStatus.DISCONTINUED);

        when(deviceRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(device));
        when(deviceModelRepository.findById(2L)).thenReturn(Optional.of(model));

        UpdateDevice request = new UpdateDevice(
                null, null, null, 2L, null, null, null, null
        );

        assertThrows(
                InvalidDeviceRequestException.class,
                () -> deviceService.updateDevice(10L, 1L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    void updateDevice_shouldRejectCustomFieldsWithModelId() {
        Device device = new Device();
        device.setId(1L);
        when(deviceRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(device));

        UpdateDevice request = new UpdateDevice(
                "Custom manufacturer", null, null, 2L, null, null, null, null
        );

        assertThrows(
                InvalidDeviceRequestException.class,
                () -> deviceService.updateDevice(10L, 1L, request)
        );

        verifyNoInteractions(deviceModelRepository);
        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    void updateDevice_shouldRejectCustomFieldsForCatalogDevice() {
        Device device = new Device();
        device.setId(1L);
        device.setModel(new DeviceModel());
        when(deviceRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(device));

        UpdateDevice request = new UpdateDevice(
                null, "Custom model", null, null, null, null, null, null
        );

        assertThrows(
                InvalidDeviceRequestException.class,
                () -> deviceService.updateDevice(10L, 1L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
        verifyNoInteractions(deviceMapper);
    }

    @Test
    void updateDevice_shouldKeepExistingValuesWhenRequestFieldsAreNull() {
        Device device = new Device();
        device.setId(1L);
        device.setCustomManufacturer("Manufacturer");
        device.setCustomModelName("Model");
        device.setCustomRatedPowerW(BigDecimal.valueOf(100));
        device.setType(DeviceType.OTHER);
        device.setName("Name");

        UpdateDevice request = new UpdateDevice(
                null, null, null, null, null, null, null, null
        );

        when(deviceRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(device));

        deviceService.updateDevice(10L, 1L, request);

        assertEquals("Manufacturer", device.getCustomManufacturer());
        assertEquals("Model", device.getCustomModelName());
        assertEquals(BigDecimal.valueOf(100), device.getCustomRatedPowerW());
        assertEquals(DeviceType.OTHER, device.getType());
        assertEquals("Name", device.getName());
        verify(deviceRepository).save(device);
    }

    @Test
    void updateDevice_shouldThrowWhenCustomRatedPowerIsNotPositive() {
        Device device = new Device();
        device.setId(1L);
        device.setCustomModelName("Model");
        device.setCustomRatedPowerW(BigDecimal.valueOf(100));
        device.setType(DeviceType.OTHER);

        UpdateDevice request = new UpdateDevice(
                null,
                null,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                null
        );

        when(deviceRepository.findByIdAndUserId(1L, 10L))
                .thenReturn(Optional.of(device));

        assertThrows(
                InvalidDeviceRequestException.class,
                () -> deviceService.updateDevice(10L, 1L, request)
        );

        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    void getSimulationTargets_shouldUseEffectiveRatedPower() {
        Device customDevice = new Device();
        customDevice.setDeviceUid(UUID.randomUUID());
        customDevice.setUserId(10L);
        customDevice.setType(DeviceType.OTHER);
        customDevice.setCustomRatedPowerW(BigDecimal.valueOf(100));

        DeviceModel model = new DeviceModel();
        model.setRatedPowerW(BigDecimal.valueOf(1200));

        Device catalogDevice = new Device();
        catalogDevice.setDeviceUid(UUID.randomUUID());
        catalogDevice.setUserId(20L);
        catalogDevice.setType(DeviceType.AIR_CONDITIONER);
        catalogDevice.setModel(model);

        when(deviceRepository.findAllByStatus(DeviceStatus.ACTIVE))
                .thenReturn(List.of(customDevice, catalogDevice));

        var result = deviceService.getSimulationTargets();

        assertEquals(BigDecimal.valueOf(100), result.get(0).ratedPowerW());
        assertEquals(BigDecimal.valueOf(1200), result.get(1).ratedPowerW());
    }

    @Test
    void getById_shouldReturnResponseWhenDeviceExists() {
        Device device = new Device();
        device.setId(1L);
        DeviceResponse response = mock(DeviceResponse.class);

        when(deviceRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(device));
        when(deviceMapper.toDeviceResponse(device)).thenReturn(response);

        assertSame(response, deviceService.getById(10L, 1L));
        verify(deviceMapper).toDeviceResponse(device);
    }

    @Test
    void getById_shouldThrowWhenDeviceDoesNotExist() {
        when(deviceRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.empty());

        assertThrows(
                DeviceNotExist.class,
                () -> deviceService.getById(10L, 1L)
        );

        verify(deviceMapper, never()).toDeviceResponse(any(Device.class));
    }

    @Test
    void getAllDevicesOfUser_shouldReturnPageResponse() {
        Device device = new Device();
        device.setId(1L);
        DeviceResponse response = mock(DeviceResponse.class);

        PageImpl<Device> page = new PageImpl<>(
                List.of(device),
                PageRequest.of(0, 10),
                1
        );

        when(deviceRepository.findAllByUserIdAndStatus(
                10L, DeviceStatus.ACTIVE, PageRequest.of(0, 10)
        )).thenReturn(page);
        when(deviceMapper.toDeviceResponse(device)).thenReturn(response);

        PageResponse<DeviceResponse> result =
                deviceService.getAllDevicesOfUser(
                        10L, DeviceStatus.ACTIVE, 0, 10
                );

        assertEquals(List.of(response), result.items());
        assertEquals(0, result.currentPage());
        assertEquals(10, result.pageSize());
        assertEquals(1, result.totalPage());
        assertTrue(result.isFirst());
        assertTrue(result.isLast());
        assertFalse(result.hasNext());
        assertFalse(result.hasPrevious());
    }

    @Test
    void getAllDevicesOfUser_shouldNormalizePageAndSize() {
        PageImpl<Device> page = new PageImpl<>(
                List.of(),
                PageRequest.of(0, 20),
                0
        );

        when(deviceRepository.findAllByUserIdAndStatus(
                10L, DeviceStatus.ACTIVE, PageRequest.of(0, 20)
        )).thenReturn(page);

        PageResponse<DeviceResponse> result =
                deviceService.getAllDevicesOfUser(
                        10L, DeviceStatus.ACTIVE, -1, 100
                );

        assertEquals(0, result.currentPage());
        assertEquals(20, result.pageSize());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void deleteDevice_shouldDeleteWhenDeviceExists() {
        Device device = new Device();
        device.setId(1L);
        when(deviceRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(device));

        deviceService.deleteDevice(10L, 1L);

        verify(deviceRepository).delete(device);
    }

    @Test
    void deleteDevice_shouldThrowWhenDeviceDoesNotExist() {
        when(deviceRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.empty());

        assertThrows(
                DeviceNotExist.class,
                () -> deviceService.deleteDevice(10L, 1L)
        );

        verify(deviceRepository, never()).delete(any(Device.class));
    }

}
