package com.codepath.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.codepath.users.Role;
import com.codepath.users.User;

import lombok.Getter;

/**
 * Authenticated principal placed in the SecurityContext. Later modules can inject it with
 * {@code @AuthenticationPrincipal UserPrincipal principal} to get the current user's id.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final Role role;

    public UserPrincipal(Long id, String name, String email, String passwordHash, Role role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getName(), user.getEmail(), user.getPasswordHash(),
                user.getRole());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
