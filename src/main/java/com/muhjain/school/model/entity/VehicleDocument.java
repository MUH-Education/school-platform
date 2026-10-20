package com.muhjain.school.model.entity;

import java.time.LocalDate;

import com.muhjain.school.model.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import com.muhjain.school.model.enums.DocType;

/**
 * One paper of one vehicle. Example: Van 4, INSURANCE, valid till 28 Oct 2026.
 * The server makes all 4 rows when a vehicle is added, {@code validTill} empty. The status
 * (MISSING, VALID, ENDING_SOON, ENDED) is calculated from the date and today.
 */
@Entity
@Table(name = "vehicle_document")
public class VehicleDocument extends BaseEntity {

	@Column(name = "vehicle_id", nullable = false)
	private Long vehicleId;

	@Enumerated(EnumType.STRING)
	@Column(name = "doc_type", nullable = false, length = 20)
	private DocType docType;

	// The last valid day. Empty until the office fills it in.
	@Column(name = "valid_till")
	private LocalDate validTill;

	protected VehicleDocument() {
	}

	public VehicleDocument(Long vehicleId, DocType docType, LocalDate validTill) {
		this.vehicleId = vehicleId;
		this.docType = docType;
		this.validTill = validTill;
	}

	public Long getVehicleId() {
		return vehicleId;
	}

	public DocType getDocType() {
		return docType;
	}

	public LocalDate getValidTill() {
		return validTill;
	}

	public void setValidTill(LocalDate validTill) {
		this.validTill = validTill;
	}

}
