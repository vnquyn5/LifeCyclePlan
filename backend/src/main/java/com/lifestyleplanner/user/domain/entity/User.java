package com.lifestyleplanner.user.domain.entity;

import jakarta.persistence.*;

import com.lifestyleplanner.user.domain.valueobject.Email;
import com.lifestyleplanner.user.domain.enums.OnboardingStatus;
import com.lifestyleplanner.user.domain.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "ux_users_email_normalized",
            columnNames = "email_normalized"
        )
    }
)
public class User {
    @Id 
    @GeneratedValue (strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column (name = "email_normalized", nullable = false, length = 254)
    private String emailNormalized;
    
    @Column (name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated (EnumType.STRING)
    @Column (nullable = false, length = 32)
    private UserStatus status;

    @Enumerated (EnumType.STRING)
    @Column (name = "onboarding_status", nullable = false, length = 32)
    private OnboardingStatus onboardingStatus;  

    @Column (name = "last_login_at")
    private Instant lastLoginAt;

    @Column (name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column (name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column (name = "delete_at")
    private Instant deleteAt;

    @Version 
    @Column (nullable = false)
    private Long version;

    protected User() {
    }

    public User(String rawEmail, String passwordHash) {
        Email email = new Email(rawEmail);
        this.email = email.value();
        this.emailNormalized = email.value();
        this.passwordHash = passwordHash;

        this.status = UserStatus.ACTIVE;
        this.onboardingStatus = OnboardingStatus.NOT_STARTED;

        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate 
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void updateLastLogin(){
        this.lastLoginAt = Instant.now();
    }

    public boolean canLogin() {
        return this.status == UserStatus.ACTIVE && this.deleteAt == null;
    }

    public boolean isDeleted() {
        return this.status == UserStatus.DELETED && this.deleteAt != null;
    }

    public boolean isDisabled() {
        return this.status == UserStatus.DISABLED;
    }

    public void startOnboarding() {
        this.onboardingStatus = OnboardingStatus.IN_PROGRESS;
    }

    public void completeOnboarding() {
        this.onboardingStatus = OnboardingStatus.COMPLETED;
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    public void softDelete() {
        this.status = UserStatus.DELETED;
        this.deleteAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }   

    public String getEmail() {
        return email;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public OnboardingStatus getOnboardingStatus() {
        return onboardingStatus;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
