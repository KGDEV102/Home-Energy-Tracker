package com.homeenergytracker.deviceservice.controller;

import com.homeenergytracker.deviceservice.config.SecurityConfig;
import com.homeenergytracker.deviceservice.dto.DeviceResponse;
import com.homeenergytracker.deviceservice.security.InternalServiceTokenFilter;
import com.homeenergytracker.deviceservice.service.DeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {DeviceController.class, InternalDeviceController.class},
        properties = "internal.service-token=test-internal-token"
)
@Import(SecurityConfig.class)
class DeviceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceService deviceService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsRequestWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/device/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passesJwtSubjectToOwnershipAwareServiceMethod() throws Exception {
        when(deviceService.getById(10L, 1L)).thenReturn(mock(DeviceResponse.class));

        mockMvc.perform(get("/api/v1/device/1")
                        .with(jwt().jwt(token -> token.subject("10"))))
                .andExpect(status().isOk());

        verify(deviceService).getById(10L, 1L);
    }

    @Test
    void rejectsInternalRequestWithoutServiceTokenEvenWithUserJwt() throws Exception {
        mockMvc.perform(get("/internal/v1/devices/simulation-targets")
                        .with(jwt().jwt(token -> token.subject("10"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInternalRequestWithWrongServiceToken() throws Exception {
        mockMvc.perform(get("/internal/v1/devices/simulation-targets")
                        .header(InternalServiceTokenFilter.HEADER_NAME, "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsInternalRequestWithMatchingServiceToken() throws Exception {
        when(deviceService.getSimulationTargets()).thenReturn(List.of());

        mockMvc.perform(get("/internal/v1/devices/simulation-targets")
                        .header(InternalServiceTokenFilter.HEADER_NAME, "test-internal-token"))
                .andExpect(status().isOk());
    }
}
