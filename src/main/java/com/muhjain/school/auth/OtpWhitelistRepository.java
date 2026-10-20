package com.muhjain.school.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpWhitelistRepository extends JpaRepository<OtpWhitelist, String> {

}
