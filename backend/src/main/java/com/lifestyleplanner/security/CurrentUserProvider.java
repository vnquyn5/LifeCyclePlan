package com.lifestyleplanner.security;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;

import com.lifestyleplanner.auth.exception.AuthErrorCode;
import com.lifestyleplanner.auth.exception.AuthException;


@Component 
public class CurrentUserProvider {
    public UUID getCurrentUserId(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if( authentication == null || !authentication.isAuthenticated()){
            throw new AuthException(AuthErrorCode.UNAUTHENTICATED);
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser user)){
            throw new AuthException(AuthErrorCode.UNAUTHENTICATED);
        }
        return user.userId();
    }
}
