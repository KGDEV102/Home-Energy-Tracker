package com.homeenergytracker.userservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name="refresh_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_refresh_tokens_token_hash",
                        columnNames = "token_hash"
                ),
                @UniqueConstraint(
                        name = "uk_refresh_tokens_replacement",
                        columnNames = "replaced_by_token_id"
                )
        })
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_refresh_tokens_user"
            )
    )
    private User user;
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            length = 64,
            columnDefinition = "char(64)",
            updatable = false
    )
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tokenHash;
    @Column(
            name = "issued_at",
            nullable = false,
            updatable = false
    )
    private Instant issuedAt;
    @Column(
            name = "expires_at",
            nullable = false,
            updatable = false
    )
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 32)
    private RefreshTokenRevocationReason revocationReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "replaced_by_token_id",
            unique = true,
            foreignKey = @ForeignKey(
                    name = "fk_refresh_tokens_replacement"
            )
    )
    private RefreshToken replacedByToken;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "created_ip", columnDefinition = "inet")
    private InetAddress createdIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @PrePersist
    void prePersist() {
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }

        if (familyId == null) {
            familyId = UUID.randomUUID();
        }
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isActive(Instant now) {
        return !isUsed()
                && !isRevoked()
                && !isExpired(now);
    }
}
