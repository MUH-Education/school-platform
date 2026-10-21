package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.Gender;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.TemplateApproval;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One approved text. Bus texts have a boy row and a girl row ({@code gender} M/F); fee texts have one row
 * ({@code gender} null). {@code body} is what the office last typed; {@code approvedBody} is what the phone
 * company (DLT) approved. The server always sends {@code approvedBody}, never the un-approved {@code body}.
 * Example: code {@code BOARDED_MORNING}, gender M, SMS, hi,
 * {@code "{name} सुबह की बस में चढ़ गया — {time}। MUH Jain School"}.
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

	protected MessageTemplate() {
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
