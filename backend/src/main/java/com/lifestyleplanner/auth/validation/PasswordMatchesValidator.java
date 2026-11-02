package com.lifestyleplanner.auth.validation;

import com.lifestyleplanner.auth.dto.request.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator
    implements ConstraintValidator<PasswordMatches, RegisterRequest> {
        @Override
        public boolean isValid(RegisterRequest request, ConstraintValidatorContext context) {
            if(request == null) {
                return true; // Không kiểm tra nếu request là null
            }
            if(request.password() == null || request.confirmPassword() == null) {
                return true; 
            }

            boolean matches = request.password().equals(request.confirmPassword());
            if(!matches) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Mật khẩu xác nhận không khớp")
                       .addPropertyNode("confirmPassword")
                       .addConstraintViolation();
}

return matches;
    }
}
