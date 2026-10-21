package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long>, JpaSpecificationExecutor<Receipt> {

	Optional<Receipt> findByReceiptNo(String receiptNo);

}
