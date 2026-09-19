package com.homeenergytracker.userservice.controller;

import com.homeenergytracker.userservice.dto.UpdateUser;
import com.homeenergytracker.userservice.dto.UserDto;
import com.homeenergytracker.userservice.entity.User;
import com.homeenergytracker.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.homeenergytracker.common.response.SuccessResponse;
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;
    UserController(UserService userService) {
        this.userService = userService;
    }
    @PostMapping
    public SuccessResponse<UserDto> createUser(@Valid @RequestBody UserDto userDto){
        return SuccessResponse.of( userService.createUser(userDto));
    }
    @PatchMapping("/{id}")
    public SuccessResponse<UserDto> updateUser(@Valid @RequestBody UpdateUser updateUser, @PathVariable Long id){
        return SuccessResponse.of(userService.updateUser(id,updateUser));
    }
    @GetMapping("/{id}")
    public SuccessResponse<UserDto> getUser(@PathVariable Long id){
        return SuccessResponse.of(userService.getUserById(id));
    }
    @DeleteMapping("/{id}")
    public SuccessResponse<?> deleteUser(@PathVariable Long id){
        userService.deleteUserById(id);
        return SuccessResponse.of();
    }

}
