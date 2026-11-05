package com.homeenergytracker.userservice.security.UserDetails;

import com.homeenergytracker.userservice.entity.User;
import com.homeenergytracker.userservice.entity.UserStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;


public class CustomUserDetails implements UserDetails {
    private final User user;
    private Long id;
    private  String username;
    private  String password;
    private final boolean enabled;
    public CustomUserDetails(User user){
        this.user = user;
        this.id =  user.getId();
        this.username = user.getEmail();
        this.password = user.getPasswordHash();
        this.enabled = UserStatus.ACTIVE.equals(user.getStatus());
    }
    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

}
