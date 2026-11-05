package com.homeenergytracker.deviceservice.integration;

import com.homeenergytracker.deviceservice.entity.Device;
import com.homeenergytracker.deviceservice.entity.DeviceModel;
import com.homeenergytracker.deviceservice.entity.DeviceModelStatus;
import com.homeenergytracker.deviceservice.entity.DeviceStatus;
import com.homeenergytracker.deviceservice.entity.DeviceType;
import com.homeenergytracker.deviceservice.repository.DeviceModelRepository;
import com.homeenergytracker.deviceservice.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "internal.service-token=test-internal-token")
@AutoConfigureMockMvc
@Import(DeviceSeviceControllerTest.MockJwtConfiguration.class)
public class DeviceSeviceControllerTest {
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @TestConfiguration
    static class MockJwtConfiguration {
        @Bean
        MockMvcBuilderCustomizer authenticatedRequestsByDefault() {
            return builder -> builder.defaultRequest(
                    get("/").with(jwt().jwt(token -> token.subject("10")))
            );
        }
    }
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceModelRepository deviceModelRepository;
    @BeforeEach
    void cleanDatabase() {
        deviceRepository.deleteAll();
    }

    @Test
    void createCustomDevice_shouldPersistDeviceAndReturnSuccess()
            throws Exception {

        String requestBody = """
            {
              "customManufacturer": "Samsung",
              "customModelName": "Custom AC",
              "customRatedPowerW": 1200.000,
              "userId": 10,
              "name": "Bedroom AC",
              "type": "AIR_CONDITIONER",
              "location": "Bedroom",
              "status": "ACTIVE"
            }
            """;

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.userId").value(10))
                .andExpect(jsonPath("$.data.name")
                        .value("Bedroom AC"))
                .andExpect(jsonPath("$.data.type")
                        .value("AIR_CONDITIONER"))
                .andExpect(jsonPath("$.data.customModelName")
                        .value("Custom AC"))
                .andExpect(jsonPath("$.data.customManufacturer")
                        .value("Samsung"))
                .andExpect(jsonPath("$.data.customRatedPowerW")
                        .value(1200.000));

        assertEquals(1, deviceRepository.count());

        Device savedDevice =
                deviceRepository.findAll().getFirst();

        assertEquals(10L, savedDevice.getUserId());
        assertEquals("Bedroom AC", savedDevice.getName());
        assertEquals("Custom AC", savedDevice.getCustomModelName());
        assertEquals("Samsung", savedDevice.getCustomManufacturer());
        assertEquals(
                new BigDecimal("1200.000"),
                savedDevice.getCustomRatedPowerW()
        );
        assertNull(savedDevice.getModel());
        assertNotNull(savedDevice.getDeviceUid());
        assertNotNull(savedDevice.getCreatedAt());
        assertNotNull(savedDevice.getUpdatedAt());
    }

    @Test
    void createCatalogDevice_shouldUseTypeFromModel()
            throws Exception {

        DeviceModel model = deviceModelRepository.findAll()
                .stream()
                .filter(item ->
                        item.getStatus() == DeviceModelStatus.ACTIVE
                )
                .findFirst()
                .orElseThrow();

        String requestBody = """
            {
              "modelId": %d,
              "userId": 10,
              "name": "Living room device",
              "location": "Living room",
              "status": "ACTIVE"
            }
            """.formatted(model.getId());

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.model.id")
                        .value(model.getId().intValue()))
                .andExpect(jsonPath("$.data.type")
                        .value(model.getType().name()))
                .andExpect(jsonPath("$.data.customModelName")
                        .value(nullValue()))
                .andExpect(jsonPath("$.data.customManufacturer")
                        .value(nullValue()));

        Device savedDevice =
                deviceRepository.findAll().getFirst();

        assertEquals(model.getId(), savedDevice.getModel().getId());
        assertEquals(model.getType(), savedDevice.getType());
        assertNull(savedDevice.getCustomModelName());
        assertNull(savedDevice.getCustomManufacturer());
    }
    @Test
    void createCustomDevice_shouldReturnBadRequestWhenModelNameMissing()
            throws Exception {

        String requestBody = """
            {
              "userId": 10,
              "name": "Bedroom device",
              "type": "OTHER",
              "location": "Bedroom"
            }
            """;

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_DEVICE_REQUEST"));

        assertEquals(0, deviceRepository.count());
    }

    @Test
    void createCustomDevice_shouldReturnBadRequestWhenTypeMissing()
            throws Exception {

        String requestBody = """
            {
              "customManufacturer": "Samsung",
              "customModelName": "Custom AC",
              "customRatedPowerW": 1200.000,
              "userId": 10,
              "name": "Bedroom device",
              "location": "Bedroom"
            }
            """;

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_DEVICE_REQUEST"));

        assertEquals(0, deviceRepository.count());
    }

    @Test
    void createCustomDevice_shouldReturnBadRequestWhenRatedPowerMissing()
            throws Exception {

        String requestBody = """
            {
              "customManufacturer": "Samsung",
              "customModelName": "Custom AC",
              "userId": 10,
              "name": "Bedroom device",
              "type": "AIR_CONDITIONER",
              "location": "Bedroom"
            }
            """;

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_DEVICE_REQUEST"));

        assertEquals(0, deviceRepository.count());
    }

    @Test
    void createCustomDevice_shouldReturnBadRequestWhenRatedPowerIsNotPositive()
            throws Exception {

        String requestBody = """
            {
              "customModelName": "Custom AC",
              "customRatedPowerW": 0,
              "userId": 10,
              "name": "Bedroom device",
              "type": "AIR_CONDITIONER",
              "location": "Bedroom"
            }
            """;

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_FAILED"));

        assertEquals(0, deviceRepository.count());
    }

    @Test
    void createDevice_shouldReturnBadRequestWhenModelNameIsBlank()
            throws Exception {

        String requestBody = """
            {
              "customModelName": "   ",
              "customRatedPowerW": 100.000,
              "userId": 10,
              "name": "Bedroom device",
              "type": "OTHER",
              "location": "Bedroom"
            }
            """;

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_FAILED"));

        assertEquals(0, deviceRepository.count());
    }

    @Test
    void createDevice_shouldReturnBadRequestWhenManufacturerIsBlank()
            throws Exception {

        String requestBody = """
            {
              "customManufacturer": "   ",
              "customModelName": "Custom AC",
              "customRatedPowerW": 1200.000,
              "userId": 10,
              "name": "Bedroom device",
              "type": "AIR_CONDITIONER",
              "location": "Bedroom"
            }
            """;

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_FAILED"));

        assertEquals(0, deviceRepository.count());
    }

    @Test
    void createDevice_shouldIgnoreSpoofedUserIdAndUseJwtSubject()
            throws Exception {

        String requestBody = """
            {
              "customModelName": "Custom AC",
              "customRatedPowerW": 1200.000,
              "userId": 0,
              "name": "Bedroom device",
              "type": "AIR_CONDITIONER",
              "location": "Bedroom"
            }
            """;

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(10));

        assertEquals(10L, deviceRepository.findAll().getFirst().getUserId());
    }

    @Test
    void createDevice_shouldReturnBadRequestWhenNameIsBlank()
            throws Exception {

        String requestBody = """
            {
              "customModelName": "Custom AC",
              "customRatedPowerW": 1200.000,
              "userId": 10,
              "name": "   ",
              "type": "AIR_CONDITIONER",
              "location": "Bedroom"
            }
            """;

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_FAILED"));

        assertEquals(0, deviceRepository.count());
    }

    @Test
    void createDevice_shouldReturnBadRequestWhenLocationIsBlank()
            throws Exception {

        String requestBody = """
            {
              "customModelName": "Custom AC",
              "customRatedPowerW": 1200.000,
              "userId": 10,
              "name": "Bedroom device",
              "type": "AIR_CONDITIONER",
              "location": "   "
            }
            """;

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_FAILED"));

        assertEquals(0, deviceRepository.count());
    }

    @Test
    void createDevice_shouldReturnBadRequestWhenModelIdIsNotPositive()
            throws Exception {

        String requestBody = """
            {
              "modelId": 0,
              "userId": 10,
              "name": "Catalog device",
              "location": "Bedroom"
            }
            """;

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_FAILED"));

        assertEquals(0, deviceRepository.count());
    }
    @Test
    void createCatalogDevice_shouldRejectCustomFields()
            throws Exception {

        DeviceModel model = deviceModelRepository.findAll()
                .stream()
                .filter(item ->
                        item.getStatus() == DeviceModelStatus.ACTIVE
                )
                .findFirst()
                .orElseThrow();

        String requestBody = """
            {
              "modelId": %d,
              "customModelName": "Wrong custom model",
              "userId": 10,
              "name": "Device",
              "location": "Room"
            }
            """.formatted(model.getId());

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_DEVICE_REQUEST"));

        assertEquals(0, deviceRepository.count());
    }
    @Test
    void createCatalogDevice_shouldReturnNotFoundWhenModelDoesNotExist()
            throws Exception {

        String requestBody = """
            {
              "modelId": 999999,
              "userId": 10,
              "name": "Device",
              "location": "Room"
            }
            """;

        mockMvc.perform(post("/api/v1/device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("DEVICE_MODEL_NOT_EXIST"));

        assertEquals(0, deviceRepository.count());
    }

    @Test
    void updateCustomDevice_shouldUpdateCommonAndCustomFields()
            throws Exception {

        Device device = persistCustomDevice();

        String requestBody = """
            {
              "customManufacturer": "LG",
              "customModelName": "Updated AC",
              "customRatedPowerW": 1500.000,
              "name": "Updated bedroom AC",
              "type": "AIR_CONDITIONER",
              "location": "Living room",
              "status": "DISABLED"
            }
            """;

        mockMvc.perform(patch("/api/v1/device/{id}", device.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id")
                        .value(device.getId().intValue()))
                .andExpect(jsonPath("$.data.name")
                        .value("Updated bedroom AC"))
                .andExpect(jsonPath("$.data.customManufacturer")
                        .value("LG"))
                .andExpect(jsonPath("$.data.customModelName")
                        .value("Updated AC"))
                .andExpect(jsonPath("$.data.customRatedPowerW")
                        .value(1500.000))
                .andExpect(jsonPath("$.data.location")
                        .value("Living room"))
                .andExpect(jsonPath("$.data.status")
                        .value("DISABLED"));

        Device updated = deviceRepository.findById(device.getId())
                .orElseThrow();
        assertEquals("Updated bedroom AC", updated.getName());
        assertEquals("LG", updated.getCustomManufacturer());
        assertEquals(
                new BigDecimal("1500.000"),
                updated.getCustomRatedPowerW()
        );
        assertEquals("Updated AC", updated.getCustomModelName());
        assertEquals(DeviceType.AIR_CONDITIONER, updated.getType());
        assertEquals(DeviceStatus.DISABLED, updated.getStatus());
        assertNull(updated.getModel());
    }

    @Test
    void updateCustomDevice_shouldSwitchToCatalogModel()
            throws Exception {

        Device device = persistCustomDevice();
        DeviceModel model = activeModel();

        String requestBody = """
            {
              "modelId": %d,
              "name": "Catalog device"
            }
            """.formatted(model.getId());

        mockMvc.perform(patch("/api/v1/device/{id}", device.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.model.id")
                        .value(model.getId().intValue()))
                .andExpect(jsonPath("$.data.type")
                        .value(model.getType().name()))
                .andExpect(jsonPath("$.data.customModelName")
                        .value(nullValue()))
                .andExpect(jsonPath("$.data.customManufacturer")
                        .value(nullValue()));

        Device updated = deviceRepository.findById(device.getId())
                .orElseThrow();
        assertEquals(model.getId(), updated.getModel().getId());
        assertEquals(model.getType(), updated.getType());
        assertNull(updated.getCustomModelName());
        assertNull(updated.getCustomManufacturer());
        assertNull(updated.getCustomRatedPowerW());
    }

    @Test
    void updateCatalogDevice_shouldRejectCustomFields()
            throws Exception {

        DeviceModel model = activeModel();
        Device device = new Device();
        device.setModel(model);
        device.setType(model.getType());
        device.setUserId(10L);
        device.setName("Catalog device");
        device.setLocation("Bedroom");
        device.setStatus(DeviceStatus.ACTIVE);
        device = deviceRepository.saveAndFlush(device);

        String requestBody = """
            {
              "customModelName": "Not allowed",
              "name": "Updated catalog device"
            }
            """;

        mockMvc.perform(patch("/api/v1/device/{id}", device.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_DEVICE_REQUEST"));

        Device unchanged = deviceRepository.findById(device.getId())
                .orElseThrow();
        assertEquals("Catalog device", unchanged.getName());
        assertNull(unchanged.getCustomModelName());
    }

    @Test
    void updateDevice_shouldReturnNotFoundWhenDeviceDoesNotExist()
            throws Exception {

        String requestBody = """
            {
              "name": "Updated device"
            }
            """;

        mockMvc.perform(patch("/api/v1/device/{id}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("DEVICE_NOT_EXIST"));
    }

    @Test
    void updateDevice_shouldHideAnotherUsersDevice() throws Exception {
        Device anotherUsersDevice = persistCustomDevice();
        anotherUsersDevice.setUserId(20L);
        anotherUsersDevice = deviceRepository.saveAndFlush(anotherUsersDevice);

        mockMvc.perform(patch("/api/v1/device/{id}", anotherUsersDevice.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Compromised name"}
                            """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEVICE_NOT_EXIST"));

        assertEquals("Bedroom AC", deviceRepository.findById(anotherUsersDevice.getId())
                .orElseThrow()
                .getName());
    }

    @Test
    void updateDevice_shouldReturnNotFoundWhenModelDoesNotExist()
            throws Exception {

        Device device = persistCustomDevice();
        String requestBody = """
            {
              "modelId": 999999,
              "name": "Updated device"
            }
            """;

        mockMvc.perform(patch("/api/v1/device/{id}", device.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("DEVICE_MODEL_NOT_EXIST"));
    }

    @Test
    void updateDevice_shouldReturnBadRequestWhenNameIsBlank()
            throws Exception {

        Device device = persistCustomDevice();
        String requestBody = """
            {
              "name": "   "
            }
            """;

        mockMvc.perform(patch("/api/v1/device/{id}", device.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_FAILED"));
    }

    @Test
    void getDevice_shouldReturnDeviceById() throws Exception {

        Device device = persistCustomDevice();

        mockMvc.perform(get("/api/v1/device/{id}", device.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id")
                        .value(device.getId().intValue()))
                .andExpect(jsonPath("$.data.userId").value(10))
                .andExpect(jsonPath("$.data.name").value("Bedroom AC"))
                .andExpect(jsonPath("$.data.customModelName")
                        .value("Custom AC"));
    }

    @Test
    void getDevice_shouldReturnNotFoundWhenDeviceDoesNotExist()
            throws Exception {

        mockMvc.perform(get("/api/v1/device/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("DEVICE_NOT_EXIST"));
    }

    @Test
    void getDevice_shouldHideAnotherUsersDevice() throws Exception {
        Device anotherUsersDevice = persistCustomDevice();
        anotherUsersDevice.setUserId(20L);
        anotherUsersDevice = deviceRepository.saveAndFlush(anotherUsersDevice);

        mockMvc.perform(get("/api/v1/device/{id}", anotherUsersDevice.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEVICE_NOT_EXIST"));
    }

    @Test
    void getAllDevices_shouldFilterByUserAndStatusAndPaginate()
            throws Exception {

        Device first = persistCustomDevice();
        first.setName("First device");

        Device second = new Device();
        second.setUserId(10L);
        second.setName("Second device");
        second.setType(DeviceType.OTHER);
        second.setCustomModelName("Second custom");
        second.setLocation("Kitchen");
        second.setStatus(DeviceStatus.ACTIVE);

        Device disabled = new Device();
        disabled.setUserId(10L);
        disabled.setName("Disabled device");
        disabled.setType(DeviceType.OTHER);
        disabled.setCustomModelName("Disabled custom");
        disabled.setLocation("Garage");
        disabled.setStatus(DeviceStatus.DISABLED);

        Device anotherUser = new Device();
        anotherUser.setUserId(20L);
        anotherUser.setName("Other user device");
        anotherUser.setType(DeviceType.OTHER);
        anotherUser.setCustomModelName("Other custom");
        anotherUser.setLocation("Office");
        anotherUser.setStatus(DeviceStatus.ACTIVE);

        deviceRepository.saveAllAndFlush(
                java.util.List.of(first, second, disabled, anotherUser));

        mockMvc.perform(get("/api/v1/device")
                        .param("userId", "10")
                        .param("deviceStatus", "ACTIVE")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.currentPage").value(0))
                .andExpect(jsonPath("$.data.pageSize").value(1))
                .andExpect(jsonPath("$.data.totalPage").value(2));
    }

    @Test
    void getAllDevices_shouldReturnBadRequestForInvalidPageParameters()
            throws Exception {

        mockMvc.perform(get("/api/v1/device")
                        .param("userId", "10")
                        .param("deviceStatus", "ACTIVE")
                        .param("page", "-1")
                        .param("size", "21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_FAILED"));
    }

    @Test
    void getAllDevices_shouldIgnoreClientUserIdAndUseJwtSubject()
            throws Exception {

        persistCustomDevice();

        Device anotherUser = new Device();
        anotherUser.setUserId(20L);
        anotherUser.setName("Other user's device");
        anotherUser.setType(DeviceType.OTHER);
        anotherUser.setCustomModelName("Other custom");
        anotherUser.setStatus(DeviceStatus.ACTIVE);
        deviceRepository.saveAndFlush(anotherUser);

        mockMvc.perform(get("/api/v1/device")
                        .param("userId", "20")
                        .param("deviceStatus", "ACTIVE")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value(10));
    }

    @Test
    void deleteDevice_shouldDeleteExistingDevice() throws Exception {

        Device device = persistCustomDevice();

        mockMvc.perform(delete("/api/v1/device/{id}", device.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Device" + device.getId()
                                + " has been deleted"));

        assertTrue(deviceRepository.findById(device.getId()).isEmpty());
    }

    @Test
    void deleteDevice_shouldReturnNotFoundWhenDeviceDoesNotExist()
            throws Exception {

        mockMvc.perform(delete("/api/v1/device/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("DEVICE_NOT_EXIST"));
    }

    @Test
    void deleteDevice_shouldHideAndPreserveAnotherUsersDevice() throws Exception {
        Device anotherUsersDevice = persistCustomDevice();
        anotherUsersDevice.setUserId(20L);
        anotherUsersDevice = deviceRepository.saveAndFlush(anotherUsersDevice);

        mockMvc.perform(delete("/api/v1/device/{id}", anotherUsersDevice.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEVICE_NOT_EXIST"));

        assertTrue(deviceRepository.existsById(anotherUsersDevice.getId()));
    }

    @Test
    void getSimulationTargets_shouldReturnCustomRatedPower()
            throws Exception {

        Device device = persistCustomDevice();

        mockMvc.perform(get("/internal/v1/devices/simulation-targets")
                        .header("X-Internal-Service-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].deviceUid")
                        .value(device.getDeviceUid().toString()))
                .andExpect(jsonPath("$.data[0].ratedPowerW")
                        .value(1200.000));
    }

    @Test
    void getSimulationTargets_shouldRejectMissingServiceToken() throws Exception {
        mockMvc.perform(get("/internal/v1/devices/simulation-targets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSimulationTargets_shouldRejectWrongServiceToken() throws Exception {
        mockMvc.perform(get("/internal/v1/devices/simulation-targets")
                        .header("X-Internal-Service-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    private Device persistCustomDevice() {
        Device device = new Device();
        device.setUserId(10L);
        device.setName("Bedroom AC");
        device.setType(DeviceType.OTHER);
        device.setCustomManufacturer("Samsung");
        device.setCustomModelName("Custom AC");
        device.setCustomRatedPowerW(new BigDecimal("1200.000"));
        device.setLocation("Bedroom");
        device.setStatus(DeviceStatus.ACTIVE);
        return deviceRepository.saveAndFlush(device);
    }

    private DeviceModel activeModel() {
        return deviceModelRepository.findAll()
                .stream()
                .filter(item -> item.getStatus() == DeviceModelStatus.ACTIVE)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void searchDeviceModels_shouldReturnActiveModels() throws Exception {

        mockMvc.perform(get("/api/v1/device-models")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.items[0].status")
                        .value("ACTIVE"));
    }
    @Test
    void searchDeviceModels_shouldFilterByKeyword() throws Exception {

        mockMvc.perform(get("/api/v1/device-models")
                        .param("keyword", "Philips")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()")
                        .value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.items[0].manufacturer")
                        .value("Philips"));
    }
    @Test
    void searchDeviceModels_shouldFilterByType() throws Exception {

        mockMvc.perform(get("/api/v1/device-models")
                        .param("type", "LIGHT")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()")
                        .value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.items[0].type")
                        .value("LIGHT"));
    }
    @Test
    void searchDeviceModels_shouldReturnEmptyItemsWhenNoModelMatches()
            throws Exception {

        mockMvc.perform(get("/api/v1/device-models")
                        .param("keyword", "model-does-not-exist")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void searchDeviceModels_shouldReturnBadRequestForInvalidPagination()
            throws Exception {

        mockMvc.perform(get("/api/v1/device-models")
                        .param("page", "-1")
                        .param("size", "21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_FAILED"));
    }
    @Test
    void searchDeviceModels_shouldReturnBadRequestForLongKeyword()
            throws Exception {

        mockMvc.perform(get("/api/v1/device-models")
                        .param("keyword", "a".repeat(101))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_FAILED"));
    }


}
