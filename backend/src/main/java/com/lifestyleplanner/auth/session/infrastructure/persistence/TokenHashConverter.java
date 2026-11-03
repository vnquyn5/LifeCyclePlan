package com.lifestyleplanner.auth.session.infrastructure.persistence;

import com.lifestyleplanner.auth.session.domain.valueobject.TokenHash;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TokenHashConverter implements AttributeConverter<TokenHash, String> {
    @Override
    public String convertToDatabaseColumn(TokenHash tokenHash) {
        return tokenHash == null ? null : tokenHash.value();
    }

    @Override
    public TokenHash convertToEntityAttribute(String value) {
        return value == null ? null : new TokenHash(value);
    }
}
