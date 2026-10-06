package com.muhjain.school.user;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByPhone(String phone);

	Optional<AppUser> findByPhoneAndActiveTrue(String phone);

	boolean existsByPhone(String phone);

	boolean existsByPhoneAndIdNot(String phone, Long id);

	List<AppUser> findAllByOrderByIdAsc();

	/**
	 * Active users of one role, with a row lock until the transaction ends.
	 * Used for the "last owner" rule, so two owners cannot turn each other off at the same moment.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<AppUser> findByRoleAndActiveTrue(Role role);

}
