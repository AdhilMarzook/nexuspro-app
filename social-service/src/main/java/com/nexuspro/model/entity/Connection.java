package com.nexuspro.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "connections",
    uniqueConstraints = @UniqueConstraint(columnNames = {"requesterId", "addresseeId"}),
    indexes = {
        @Index(name = "idx_conn_requester", columnList = "requesterId"),
        @Index(name = "idx_conn_addressee", columnList = "addresseeId"),
        @Index(name = "idx_conn_status",    columnList = "status")
    })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Connection {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false) private UUID requesterId;
    @Column(nullable = false) private UUID addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(length = 500) private String message;    // Optional connection note

    @CreationTimestamp private Instant createdAt;
    @Column           private Instant respondedAt;

    public enum Status { PENDING, ACCEPTED, DECLINED, BLOCKED }
}
