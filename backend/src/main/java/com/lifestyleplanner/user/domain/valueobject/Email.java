package com.lifestyleplanner.user.domain.valueobject;

import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String value){
    private static final Pattern EMAIL_PATTERN = 
    Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    
public Email {
    if(value == null || value.isBlank()){
        throw new IllegalArgumentException("Email không được để trống");
    }

    value = normalize(value);

    if(value.length() > 254){
        throw new IllegalArgumentException("Email không được vượt quá 254 ký tự");
    }

    if(!EMAIL_PATTERN.matcher(value).matches()){
        throw new IllegalArgumentException("Email không đúng định dạng");
    }
}
    public static String normalize(String email){
        if(email == null){
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
