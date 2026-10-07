package com.muhjain.school.vehicle;

import java.time.LocalDate;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One paper of one vehicle. Example: Van 4, INSURANCE, valid till 28 Oct 2026.
 * Only the date is stored. The status (VALID, ENDING_SOON, ENDED) is calculated from it and today.
 */
@Entity
@Table(name = "vehicle_document")
public class VehicleDocument extends BaseEntity {

	@Column(name = "vehicle_id", nullable = false)
	private Long vehicleId;

	@Enumerated(EnumType.STRING)
	@Column(name = "doc_type", nullable = false, length = 20)
	private DocType docType;

	// The last valid day.
	@Column(name = "valid_till", nullable = false)
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
