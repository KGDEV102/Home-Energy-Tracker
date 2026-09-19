package com.homeenergytracker.userservice.service;

import com.homeenergytracker.userservice.dto.UpdateUser;
import com.homeenergytracker.userservice.dto.UserDto;
import com.homeenergytracker.userservice.entity.User;
import com.homeenergytracker.userservice.entity.UserStatus;
import com.homeenergytracker.userservice.exception.EmailAlreadyExistsException;
import com.homeenergytracker.userservice.exception.UserNotExists;
import com.homeenergytracker.userservice.mapper.UserMapper;
import com.homeenergytracker.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "kiet@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    @Test
    void createUser_shouldReturnCreatedUser_whenEmailDoesNotExist() {
        // Arrange
        UserDto request = createRequest();
        User entity = createEntity();
        UserDto expectedResponse = createResponse();

        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(entity);
        when(userRepository.save(entity)).thenReturn(entity);
        when(userMapper.toDto(entity)).thenReturn(expectedResponse);

        // Act
        UserDto actualResponse = userService.createUser(request);

        // Assert
        assertThat(actualResponse).isEqualTo(expectedResponse);
        verify(userRepository).existsByEmail(EMAIL);
        verify(userMapper).toEntity(request);
        verify(userRepository).save(entity);
        verify(userMapper).toDto(entity);
    }

    @Test
    void createUser_shouldThrowException_whenEmailAlreadyExists() {
        // Arrange
        UserDto request = createRequest();
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("Email already exists");

        verify(userMapper, never()).toEntity(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldThrowException_whenUserDoesNotExist() {
        // Arrange
        UpdateUser request = new UpdateUser("New name", null, null, null, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> userService.updateUser(USER_ID, request))
                .isInstanceOf(UserNotExists.class)
                .hasMessage("User not exists");

        verify(userMapper, never()).updateEntity(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldThrowException_whenEmailBelongsToAnotherUser() {
        // Arrange
        User existingUser = createEntity();
        UpdateUser request = new UpdateUser(
                null,
                null,
                "other@example.com",
                null,
                null
        );

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmailAndIdNot("other@example.com", USER_ID))
                .thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> userService.updateUser(USER_ID, request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("Email already exists");

        verify(userMapper, never()).updateEntity(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldUpdateOtherFields_withoutCheckingEmail_whenEmailIsNull() {
        // Arrange
        User existingUser = createEntity();
        UpdateUser request = new UpdateUser(
                "Updated Kiet",
                null,
                null,
                "Ha Noi",
                null
        );
        UserDto expectedResponse = createResponse();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);
        when(userMapper.toDto(existingUser)).thenReturn(expectedResponse);

        // Act
        UserDto actualResponse = userService.updateUser(USER_ID, request);

        // Assert
        assertThat(actualResponse).isEqualTo(expectedResponse);
        verify(userRepository, never()).existsByEmailAndIdNot(any(), any());
        verify(userMapper).updateEntity(request, existingUser);
        verify(userRepository).save(existingUser);
        verify(userMapper).toDto(existingUser);
    }

    @Test
    void updateUser_shouldSucceed_whenKeepingCurrentEmail() {
        // Arrange
        User existingUser = createEntity();
        UpdateUser request = new UpdateUser(null, null, EMAIL, null, null);
        UserDto expectedResponse = createResponse();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmailAndIdNot(EMAIL, USER_ID)).thenReturn(false);
        when(userRepository.save(existingUser)).thenReturn(existingUser);
        when(userMapper.toDto(existingUser)).thenReturn(expectedResponse);

        // Act
        UserDto actualResponse = userService.updateUser(USER_ID, request);

        // Assert
        assertThat(actualResponse).isEqualTo(expectedResponse);
        verify(userRepository).existsByEmailAndIdNot(EMAIL, USER_ID);
        verify(userMapper).updateEntity(request, existingUser);
        verify(userRepository).save(existingUser);
    }

    @Test
    void getUserById_shouldReturnUser_whenUserExists() {
        // Arrange
        User user = createEntity();
        UserDto expectedResponse = createResponse();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(expectedResponse);

        // Act
        UserDto actualResponse = userService.getUserById(USER_ID);

        // Assert
        assertThat(actualResponse).isEqualTo(expectedResponse);
        verify(userMapper).toDto(user);
    }

    @Test
    void getUserById_shouldThrowException_whenUserDoesNotExist() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> userService.getUserById(USER_ID))
                .isInstanceOf(UserNotExists.class)
                .hasMessage("User not exists");

        verify(userMapper, never()).toDto(any());
    }

    @Test
    void deleteUserById_shouldDeleteUser_whenUserExists() {
        // Arrange
        User user = createEntity();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        // Act
        userService.deleteUserById(USER_ID);

        // Assert
        verify(userRepository).delete(user);
    }

    @Test
    void deleteUserById_shouldThrowException_whenUserDoesNotExist() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> userService.deleteUserById(USER_ID))
                .isInstanceOf(UserNotExists.class)
                .hasMessage("User not exists");

        verify(userRepository, never()).delete(any());
    }

    private UserDto createRequest() {
        return new UserDto(
                null,
                "Kiet",
                "Nguyen",
                EMAIL,
                "Ho Chi Minh City",
                null,
                null,
                null
        );
    }

    private User createEntity() {
        User user = new User();
        user.setId(USER_ID);
        user.setFirstName("Kiet");
        user.setLastName("Nguyen");
        user.setEmail(EMAIL);
        user.setAddress("Ho Chi Minh City");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private UserDto createResponse() {
        Instant now = Instant.parse("2026-09-19T00:00:00Z");
        return new UserDto(
                USER_ID,
                "Kiet",
                "Nguyen",
                EMAIL,
                "Ho Chi Minh City",
                UserStatus.ACTIVE,
                now,
                now
        );
    }
}
