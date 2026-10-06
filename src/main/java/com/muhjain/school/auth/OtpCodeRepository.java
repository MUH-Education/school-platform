package com.muhjain.school.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

	/** The newest code of a phone, used or not. For the "wait 60 seconds" rule. */
	Optional<OtpCode> findFirstByPhoneOrderByCreatedAtDescIdDesc(String phone);

	/** The newest code of a phone that is not used yet. Only this one can log in. */
	Optional<OtpCode> findFirstByPhoneAndConsumedAtIsNullOrderByCreatedAtDescIdDesc(String phone);

	/** The oldest code of a phone after a time. For "when can this phone ask again". */
	Optional<OtpCode> findFirstByPhoneAndCreatedAtAfterOrderByCreatedAtAsc(String phone, Instant since);

	Optional<OtpCode> findFirstByRequestIpAndCreatedAtAfterOrderByCreatedAtAsc(String requestIp, Instant since);

	long countByPhoneAndCreatedAtAfter(String phone, Instant since);

	long countByRequestIpAndCreatedAtAfter(String requestIp, Instant since);

	@Modifying
	@Query("delete from OtpCode o where o.createdAt < :before")
	int deleteCreatedBefore(Instant before);

}
