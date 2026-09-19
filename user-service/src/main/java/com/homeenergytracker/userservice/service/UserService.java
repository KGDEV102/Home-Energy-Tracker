package com.homeenergytracker.userservice.service;

import com.homeenergytracker.userservice.dto.UpdateUser;
import com.homeenergytracker.userservice.dto.UserDto;
import com.homeenergytracker.userservice.entity.User;
import com.homeenergytracker.userservice.exception.EmailAlreadyExistsException;
import com.homeenergytracker.userservice.exception.UserNotExists;
import com.homeenergytracker.userservice.mapper.UserMapper;
import com.homeenergytracker.userservice.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }
     public UserDto createUser(UserDto userDto) {
        if(userRepository.existsByEmail(userDto.email())){
           throw new EmailAlreadyExistsException();
        }
        User user =  userMapper.toEntity(userDto);
        user.setEmail(user.getEmail().trim().toLowerCase(Locale.ROOT));
        userRepository.save(user);
        return userMapper.toDto(user);

    }
    public UserDto updateUser(Long id, UpdateUser updateUser) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotExists());

        // Không coi email hiện tại của chính user là email bị trùng.
        if (updateUser.email() != null
                && userRepository.existsByEmailAndIdNot(updateUser.email(), id)) {
            throw new EmailAlreadyExistsException();
        }

        userMapper.updateEntity(updateUser, user);
        user.setEmail(user.getEmail().trim().toLowerCase(Locale.ROOT));

        User savedUser = userRepository.save(user);

        return userMapper.toDto(savedUser);
    }
    public UserDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotExists());
        return userMapper.toDto(user);
    }
    public void deleteUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotExists());
        userRepository.delete(user);
    }

}
