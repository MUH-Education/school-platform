package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.ButtonAction;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One WhatsApp message that came in to the school number (from the provider's webhook). Used for STOP, button
 * taps, the 24-hour window and "promised to pay by". The inbox screen is the provider's for now.
 * Example: +919811100001, "शुक्रवार तक जमा कर देंगे", guardian 210, handled, promised until 23 Oct 2026.
 */
@Entity
@Table(name = "inbound_message")
public class InboundMessage extends BaseEntity {

	// Any country: "+" and 7 to 15 digits. Never log it unmasked.
	@Column(nullable = false, length = 16)
	private String phone;

	@Column(name = "guardian_id")
	private Long guardianId;

	@Column(name = "staff_id")
	private Long staffId;

	@Column(name = "enquiry_id")
	private Long enquiryId;

	// Null for a button tap. Never log it.
	@Column(length = 4096)
	private String text;

	@Enumerated(EnumType.STRING)
	@Column(name = "button_action", length = 30)
	private ButtonAction buttonAction;

	@Column(name = "in_reply_to_outbox_id")
	private Long inReplyToOutboxId;

	// The provider's id of the message: the same webhook twice saves one row.
	@Column(name = "provider_ref", nullable = false, unique = true, length = 120)
	private String providerRef;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	@Column(nullable = false)
	private boolean handled;

	// Empty and handled = done by the server itself (a STOP).
	@Column(name = "handled_by")
	private Long handledBy;

	@Column(length = 300)
	private String note;

	// "Will pay by Friday": no fee reminder to this family until this day (included).
	@Column(name = "promised_until")
	private LocalDate promisedUntil;

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public Long getGuardianId() {
		return guardianId;
	}

	public void setGuardianId(Long guardianId) {
		this.guardianId = guardianId;
	}

	public Long getStaffId() {
		return staffId;
	}

	public void setStaffId(Long staffId) {
		this.staffId = staffId;
	}

	public Long getEnquiryId() {
		return enquiryId;
	}

	public void setEnquiryId(Long enquiryId) {
		this.enquiryId = enquiryId;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public ButtonAction getButtonAction() {
		return buttonAction;
	}

	public void setButtonAction(ButtonAction buttonAction) {
		this.buttonAction = buttonAction;
	}

	public Long getInReplyToOutboxId() {
		return inReplyToOutboxId;
	}

	public void setInReplyToOutboxId(Long inReplyToOutboxId) {
		this.inReplyToOutboxId = inReplyToOutboxId;
	}

	public String getProviderRef() {
		return providerRef;
	}

	public void setProviderRef(String providerRef) {
		this.providerRef = providerRef;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

	public void setReceivedAt(Instant receivedAt) {
		this.receivedAt = receivedAt;
	}

	public boolean isHandled() {
		return handled;
	}

	public void setHandled(boolean handled) {
		this.handled = handled;
	}

	public Long getHandledBy() {
		return handledBy;
	}

	public void setHandledBy(Long handledBy) {
		this.handledBy = handledBy;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public LocalDate getPromisedUntil() {
		return promisedUntil;
	}

	public void setPromisedUntil(LocalDate promisedUntil) {
		this.promisedUntil = promisedUntil;
	}

}
