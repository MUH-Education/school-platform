package com.muhjain.school.messaging;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One approved text. The primary key is the code. Example: {@code BOARDED_MORNING_M}, SMS, hi,
 * {@code "{name} सुबह की बस में चढ़ गया — {time}। MUH Jain School"}.
 * The provider's template id is filled in after DLT approval.
 */
@Entity
@Table(name = "message_template")
public class MessageTemplate {

	@Id
	@Column(length = 40)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MessageChannel channel;

	@Column(nullable = false, length = 5)
	private String language;

	@Column(nullable = false, length = 500)
	private String body;

	@Column(name = "provider_template_id", length = 60)
	private String providerTemplateId;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false, updatable = false, insertable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected MessageTemplate() {
	}

	public String getCode() {
		return code;
	}

	public MessageChannel getChannel() {
		return channel;
	}

	public String getLanguage() {
		return language;
	}

	public String getBody() {
		return body;
	}

	public String getProviderTemplateId() {
		return providerTemplateId;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	/** Changes the text and the provider id. {@code now} comes from the app Clock. */
	public void change(String body, String providerTemplateId, boolean active, Instant now) {
		this.body = body;
		this.providerTemplateId = providerTemplateId;
		this.active = active;
		this.updatedAt = now;
	}

}
