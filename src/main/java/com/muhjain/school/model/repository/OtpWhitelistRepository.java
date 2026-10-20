package com.muhjain.school.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.muhjain.school.model.entity.OtpWhitelist;

public interface OtpWhitelistRepository extends JpaRepository<OtpWhitelist, String> {

}
