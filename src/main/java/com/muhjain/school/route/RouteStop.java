package com.muhjain.school.route;

import java.time.LocalTime;

import com.muhjain.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One stop of a route. Example: Route 4, number 2, "Jakhal", morning 07:40.
 * The evening order is the morning order reversed, so there is no evening number.
 */
@Entity
@Table(name = "route_stop")
public class RouteStop extends BaseEntity {

	@Column(name = "route_id", nullable = false)
	private Long routeId;

	@Column(nullable = false, length = 80)
	private String name;

	// 1, 2, 3 in morning order. Set by the server from the order of the list (rule 14).
	@Column(name = "seq_no", nullable = false)
	private int seqNo;

	@Column(name = "morning_time")
	private LocalTime morningTime;

	@Column(name = "evening_time")
	private LocalTime eveningTime;

	protected RouteStop() {
	}

	public RouteStop(Long routeId, String name, int seqNo, LocalTime morningTime, LocalTime eveningTime) {
		this.routeId = routeId;
		this.name = name;
		this.seqNo = seqNo;
		this.morningTime = morningTime;
		this.eveningTime = eveningTime;
	}

	public Long getRouteId() {
		return routeId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getSeqNo() {
		return seqNo;
	}

	public void setSeqNo(int seqNo) {
		this.seqNo = seqNo;
	}

	public LocalTime getMorningTime() {
		return morningTime;
	}

	public void setMorningTime(LocalTime morningTime) {
		this.morningTime = morningTime;
	}

	public LocalTime getEveningTime() {
		return eveningTime;
	}

	public void setEveningTime(LocalTime eveningTime) {
		this.eveningTime = eveningTime;
	}

}
