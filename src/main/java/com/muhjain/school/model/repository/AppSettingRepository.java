package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {

	List<AppSetting> findAllByOrderByKeyAsc();

}
