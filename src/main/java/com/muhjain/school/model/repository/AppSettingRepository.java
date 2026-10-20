package com.muhjain.school.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.muhjain.school.model.entity.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {

	List<AppSetting> findAllByOrderByKeyAsc();

}
