package com.lifestyleplanner.auth.domain.service;

import org.springframework.stereotype.Service;

import com.lifestyleplanner.auth.exception.AuthErrorCode;
import com.lifestyleplanner.auth.exception.AuthException;
import com.lifestyleplanner.user.domain.entity.User;

@Service 
public class AuthDomainService {
    public void validateUserCanLogin(User user){
        if (user.isDisabled()) {
            throw new AuthException(AuthErrorCode.ACCOUNT_DISABLED);
        }

        if (user.isDeleted()) {
            throw new AuthException(AuthErrorCode.ACCOUNT_DELETED);
        }

        if(!user.canLogin()){
            throw new AuthException(AuthErrorCode.ACCOUNT_DISABLED);
        }
    }
}
