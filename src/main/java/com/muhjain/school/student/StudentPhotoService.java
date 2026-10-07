package com.muhjain.school.student;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A student's photo. Rules 18 to 21 of docs/phases/phase-3-students-admission.md.
 * <ul>
 * <li>JPEG or PNG, up to 2 MB, checked from the first bytes.</li>
 * <li>Saved through {@link PhotoStorage}. The key is in {@code student.photo_key}.</li>
 * <li>A new photo removes the old file, after the new row is saved.</li>
 * </ul>
 * There is no public URL. The controller checks STUDENTS_VIEW before it streams a photo.
 */
@Service
public class StudentPhotoService {

	private static final Logger log = LoggerFactory.getLogger(StudentPhotoService.class);

	private final StudentRepository students;

	private final PhotoStorage storage;

	private final AuditService auditService;

	public StudentPhotoService(StudentRepository students, PhotoStorage storage, AuditService auditService) {
		this.students = students;
		this.storage = storage;
		this.auditService = auditService;
	}

	/**
	 * Rules 18, 19, 21.
	 *
	 * @throws ApiException 404 NOT_FOUND, 400 VALIDATION (empty, too big, not a JPEG or PNG)
	 */
	@Transactional
	public PhotoResponse upload(Long studentId, byte[] bytes) {
		Student student = students.findById(studentId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student does not exist."));
		if (bytes == null || bytes.length == 0) {
			throw ApiException.validation("file", "is empty");
		}
		if (bytes.length > PhotoRules.MAX_BYTES) {
			throw ApiException.validation("file", "must be at most 2 MB");
		}
		PhotoRules.Kind kind = PhotoRules.detect(bytes)
			.orElseThrow(() -> ApiException.validation("file", "must be a JPEG or PNG picture"));

		String oldKey = student.getPhotoKey();
		String newKey = write(bytes, kind);
		student.setPhotoKey(newKey);
		students.save(student);
		auditService.record("STUDENT", studentId, AuditAction.UPDATED, (oldKey == null) ? "Photo added." : "Photo replaced.",
				Map.of("photo", (oldKey == null) ? "added" : "replaced"));

		// The old file goes only after the database change is saved. A failed save removes the new file instead.
		afterTransaction(committed -> deleteQuietly(committed ? oldKey : newKey));
		return new PhotoResponse(true);
	}

	/** Rule 20 (the permission is checked by the controller). @throws ApiException 404 NOT_FOUND */
	@Transactional(readOnly = true)
	public PhotoContent open(Long studentId) {
		Student student = students.findById(studentId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student does not exist."));
		String key = student.getPhotoKey();
		if (key == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student has no photo.");
		}
		try (InputStream in = storage.open(key)) {
			PhotoRules.Kind kind = PhotoRules.Kind.ofExtension(key).orElse(PhotoRules.Kind.JPEG);
			return new PhotoContent(in.readAllBytes(), kind.contentType());
		}
		catch (IOException ex) {
			log.error("Photo file of student {} could not be read", studentId, ex);
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student has no photo.");
		}
	}

	/** Removes the photo. A student with no photo: nothing happens. @throws ApiException 404 NOT_FOUND */
	@Transactional
	public PhotoResponse remove(Long studentId) {
		Student student = students.findById(studentId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student does not exist."));
		String oldKey = student.getPhotoKey();
		if (oldKey != null) {
			student.setPhotoKey(null);
			students.save(student);
			auditService.record("STUDENT", studentId, AuditAction.UPDATED, "Photo removed.", Map.of("photo", "removed"));
			afterTransaction(committed -> {
				if (committed) {
					deleteQuietly(oldKey);
				}
			});
		}
		return new PhotoResponse(false);
	}

	private String write(byte[] bytes, PhotoRules.Kind kind) {
		try {
			return storage.save(bytes, kind.extension());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The photo could not be saved", ex);
		}
	}

	private void deleteQuietly(String key) {
		if (key == null) {
			return;
		}
		try {
			storage.delete(key);
		}
		catch (IOException ex) {
			log.warn("A photo file could not be removed: {}", key, ex);
		}
	}

	private static void afterTransaction(java.util.function.Consumer<Boolean> action) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				action.accept(status == STATUS_COMMITTED);
			}
		});
	}

}
