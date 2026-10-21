package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.Gender;
import com.muhjain.school.model.enums.HeaderType;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.TemplateApproval;
import com.muhjain.school.model.enums.TemplateKind;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * One approved text. Bus texts have a boy row and a girl row ({@code gender} M/F); fee texts have one row
 * ({@code gender} null). {@code body} is what the office last typed; {@code approvedBody} is what the phone
 * company (DLT) approved. The server always sends {@code approvedBody}, never the un-approved {@code body}.
 * Example: code {@code BOARDED_MORNING}, gender M, SMS, hi,
 * {@code "{name} सुबह की बस में चढ़ गया — {time}। MUH Jain School"}.
 * <p>
 * Since Phase 11 the same table holds notification templates ({@code kind} NOTICE, MARKETING, ...). They have no
 * code, are found by id, and editing an APPROVED one makes a new row ({@code replacesId}).
 */
@Entity
@Table(name = "message_template")
public class MessageTemplate extends BaseEntity {

	@Column(length = 40)
	private String code;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 1)
	private Gender gender;

	@Column(length = 60)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MessageChannel channel;

	@Column(nullable = false, length = 5)
	private String language = "hi";

	@Column(nullable = false, length = 1000)
	private String body;

	@Column(name = "approved_body", length = 1000)
	private String approvedBody;

	@Column(name = "provider_template_id", length = 60)
	private String providerTemplateId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TemplateApproval approval = TemplateApproval.WAITING;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "updated_by")
	private Long updatedBy;

	// SYSTEM for the Phase 5 texts. Set when the row is made, never changed.
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private TemplateKind kind;

	@Enumerated(EnumType.STRING)
	@Column(name = "header_type", nullable = false, length = 10)
	private HeaderType headerType = HeaderType.NONE;

	// The sample file the template was approved with (our storage key).
	@Column(name = "media_key", length = 120)
	private String mediaKey;

	// The short SMS version, null = no SMS.
	@Column(name = "sms_body", length = 320)
	private String smsBody;

	// One example per {box}, WhatsApp asks for it. Example: {"Parent name": "Rajesh Punia"}
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "example_values", nullable = false, columnDefinition = "jsonb")
	private Map<String, String> exampleValues = new HashMap<>();

	// What the provider calls it. Example: open_day_oct
	@Column(name = "provider_template_name", length = 60)
	private String providerTemplateName;

	@Column(name = "provider_language", nullable = false, length = 10)
	private String providerLanguage = "hi";

	// The provider's text when approval is REJECTED.
	@Column(name = "rejection_reason", length = 300)
	private String rejectionReason;

	@Column(name = "replaces_id")
	private Long replacesId;

	@Column(name = "created_by", updatable = false)
	private Long createdBy;

	protected MessageTemplate() {
	}

	/** A new notification template: WhatsApp, no code, WAITING for approval. Example: MARKETING "Open day". */
	public MessageTemplate(TemplateKind kind, String name, String body, Long createdBy) {
		if (kind == TemplateKind.SYSTEM) {
			throw new IllegalArgumentException("SYSTEM templates come only from Flyway");
		}
		this.kind = kind;
		this.name = name;
		this.body = body;
		this.channel = MessageChannel.WHATSAPP;
		this.createdBy = createdBy;
		this.updatedBy = createdBy;
	}

	public String getCode() {
		return code;
	}

	public Gender getGender() {
		return gender;
	}

	public String getName() {
		return name;
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

	public String getApprovedBody() {
		return approvedBody;
	}

	public String getProviderTemplateId() {
		return providerTemplateId;
	}

	public TemplateApproval getApproval() {
		return approval;
	}

	public boolean isActive() {
		return active;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public TemplateKind getKind() {
		return kind;
	}

	public HeaderType getHeaderType() {
		return headerType;
	}

	public void setHeaderType(HeaderType headerType) {
		this.headerType = headerType;
	}

	public String getMediaKey() {
		return mediaKey;
	}

	public void setMediaKey(String mediaKey) {
		this.mediaKey = mediaKey;
	}

	public String getSmsBody() {
		return smsBody;
	}

	public void setSmsBody(String smsBody) {
		this.smsBody = smsBody;
	}

	public Map<String, String> getExampleValues() {
		return exampleValues;
	}

	public void setExampleValues(Map<String, String> exampleValues) {
		this.exampleValues = (exampleValues == null) ? new HashMap<>() : new HashMap<>(exampleValues);
	}

	public String getProviderTemplateName() {
		return providerTemplateName;
	}

	public void setProviderTemplateName(String providerTemplateName) {
		this.providerTemplateName = providerTemplateName;
	}

	public String getProviderLanguage() {
		return providerLanguage;
	}

	public void setProviderLanguage(String providerLanguage) {
		this.providerLanguage = providerLanguage;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public Long getReplacesId() {
		return replacesId;
	}

	public void setReplacesId(Long replacesId) {
		this.replacesId = replacesId;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setBody(String body) {
		this.body = body;
	}

	/** The text the provider approved. For a notification template it is the body at the moment of approval. */
	public void setApprovedBody(String approvedBody) {
		this.approvedBody = approvedBody;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	/**
	 * The provider's answer (or the owner's, in local/test). The reason is kept only for REJECTED; the database
	 * refuses a reason on any other status.
	 */
	public void setApproval(TemplateApproval approval, String rejectionReason, Long updatedBy) {
		this.approval = approval;
		this.rejectionReason = (approval == TemplateApproval.REJECTED) ? rejectionReason : null;
		this.updatedBy = updatedBy;
	}

	/** The text to actually send: the DLT-approved one, or the typed one while none is approved yet. */
	public String textToSend() {
		return (approvedBody != null) ? approvedBody : body;
	}

	/** Changes the text and the provider id. The approval status and {@code approvedBody} are untouched here:
	 * they only change once the phone company approves the new text (a later, separate step). */
	public void change(String body, String providerTemplateId, boolean active, Long updatedBy) {
		this.body = body;
		this.providerTemplateId = providerTemplateId;
		this.active = active;
		this.updatedBy = updatedBy;
	}

}
