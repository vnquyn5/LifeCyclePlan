package com.lifestyleplanner.security;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class SecurityConfigTest {

    @Test
    void doesNotProvideAnImplicitInMemoryLogin() {
        var userDetailsService = new SecurityConfig(null, null).userDetailsService();

        assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("synthetic-user"));
    }
}
