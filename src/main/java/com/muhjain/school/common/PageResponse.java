package com.muhjain.school.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * One page of a long list (students, enquiries, messages, audit).
 * Example: {@code { "items": [ ... ], "page": 0, "size": 25, "totalItems": 290, "totalPages": 12 }}
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

}
