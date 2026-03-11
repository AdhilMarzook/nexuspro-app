package com.nexuspro.repository;

import com.nexuspro.model.entity.Connection;
import com.nexuspro.model.entity.Connection.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConnectionRepository extends JpaRepository<Connection, UUID> {

    @Query("""
        SELECT c FROM Connection c
        WHERE ((c.requesterId = :u1 AND c.addresseeId = :u2)
            OR (c.requesterId = :u2 AND c.addresseeId = :u1))
        """)
    Optional<Connection> findBetween(UUID u1, UUID u2);

    Page<Connection> findByAddresseeIdAndStatus(UUID addresseeId, Status status, Pageable pageable);

    Page<Connection> findByRequesterIdAndStatus(UUID requesterId, Status status, Pageable pageable);

    @Query("""
        SELECT COUNT(c) FROM Connection c
        WHERE (c.requesterId = :userId OR c.addresseeId = :userId)
          AND c.status = 'ACCEPTED'
        """)
    long countConnections(UUID userId);

    @Query("""
        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
        FROM Connection c
        WHERE ((c.requesterId = :u1 AND c.addresseeId = :u2)
            OR (c.requesterId = :u2 AND c.addresseeId = :u1))
          AND c.status = 'ACCEPTED'
        """)
    boolean areConnected(UUID u1, UUID u2);
}
