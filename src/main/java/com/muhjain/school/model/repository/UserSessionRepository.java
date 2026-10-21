package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

	/** Used only when Redis has forgotten this session (restart, eviction) — the rare fallback path. */
	Optional<UserSession> findBySessionHash(String sessionHash);

	/**
	 * "Log out everywhere": marks every session of this user revoked, so even a session Redis still
	 * remembers fails the next time it has to fall back to this table. The Redis entries themselves
	 * are cleared separately, from the {@code user_sessions:<userId>} set (SessionService).
	 */
	@Modifying
	@Query("update UserSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
	int revokeAllForUser(Long userId, Instant now);

}
