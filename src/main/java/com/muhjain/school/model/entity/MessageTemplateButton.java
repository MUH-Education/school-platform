package com.muhjain.school.model.entity;

import com.muhjain.school.model.enums.ButtonType;
import jakarta.persistence.*;

/**
 * One of up to 3 WhatsApp buttons under a notification template.
 * Example: template 31, seq 3, REPLY, "Stop messages", action STOP_MESSAGES. For CALL the action is the phone,
 * for URL the link (the action is text, because it is not always a {@code ButtonAction}).
 */
@Entity
@Table(name = "message_template_button")
public class MessageTemplateButton extends BaseEntity {

	@Column(name = "template_id", nullable = false)
	private Long templateId;

	@Column(name = "seq_no", nullable = false)
	private int seqNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ButtonType type;

	@Column(nullable = false, length = 25)
	private String text;

	@Column(nullable = false, length = 300)
	private String action;

	public Long getTemplateId() {
		return templateId;
	}

	public void setTemplateId(Long templateId) {
		this.templateId = templateId;
	}

	public int getSeqNo() {
		return seqNo;
	}

	public void setSeqNo(int seqNo) {
		this.seqNo = seqNo;
	}

	public ButtonType getType() {
		return type;
	}

	public void setType(ButtonType type) {
		this.type = type;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

}
