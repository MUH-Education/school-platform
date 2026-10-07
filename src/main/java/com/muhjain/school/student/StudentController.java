package com.muhjain.school.student;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Students screens. Reading needs STUDENTS_VIEW, changing needs STUDENTS_EDIT.
 * Students are never deleted: a child who leaves gets status LEFT.
 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

	private final StudentService studentService;

	public StudentController(StudentService studentService) {
		this.studentService = studentService;
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('STUDENTS_EDIT')")
	public StudentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateStudentRequest request) {
		return studentService.update(id, request);
	}

}
