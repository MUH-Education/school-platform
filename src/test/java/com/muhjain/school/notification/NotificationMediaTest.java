package com.muhjain.school.notification;

import com.jayway.jsonpath.JsonPath;
import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.config.StorageProperties;
import com.muhjain.school.model.enums.Role;
import com.muhjain.school.service.storage.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.muhjain.school.notification.MediaFiles.ascii;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code POST /api/v1/notification-media}: the file is ours first (rule 10), with limits by type. */
class NotificationMediaTest extends AbstractIntegrationTest {

	private static final int MB = 1024 * 1024;

	@Autowired
	private FileStorage storage;

	@Autowired
	private StorageProperties storageProperties;

	private final List<String> savedKeys = new ArrayList<>();

	private String owner;

	@BeforeEach
	void owner() {
		owner = tokenFor(addUser("+919812340001", Role.OWNER));
	}

	@AfterEach
	void removeSavedFiles() throws IOException {
		for (String key : savedKeys) {
			storage.delete(key);
		}
	}

	private ResultActions upload(String fileName, byte[] bytes) throws Exception {
		return mockMvc.perform(multipart("/api/v1/notification-media")
			.file(new MockMultipartFile("file", fileName, "application/octet-stream", bytes))
			.header("Authorization", bearer(owner)));
	}

	private String keyOf(ResultActions result) throws Exception {
		String key = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.mediaKey");
		savedKeys.add(key);
		return key;
	}

	private long filesInNotificationFolder() throws IOException {
		Path folder = storageProperties.folder().resolve("notifications");
		if (!Files.isDirectory(folder)) {
			return 0;
		}
		try (Stream<Path> files = Files.list(folder)) {
			return files.count();
		}
	}

	@Test
	void ownerUploadsAPoster() throws Exception {
		byte[] poster = MediaFiles.png(1080, 1350);
		ResultActions result = upload("diwali.png", poster).andExpect(status().isOk())
			.andExpect(jsonPath("$.mediaKey", startsWith("notifications/")))
			.andExpect(jsonPath("$.mediaType").value("IMAGE"))
			.andExpect(jsonPath("$.bytes").value(poster.length))
			.andExpect(jsonPath("$.width").value(1080))
			.andExpect(jsonPath("$.height").value(1350))
			.andExpect(jsonPath("$.seconds").doesNotExist());
		String key = keyOf(result);
		assertThat(key).endsWith(".png");
		try (InputStream in = storage.open(key)) {
			assertThat(in.readAllBytes()).isEqualTo(poster);
		}
	}

	@Test
	void aVideoGetsItsLengthAndAPdfIsADocument() throws Exception {
		keyOf(upload("diwali.mp4", MediaFiles.mp4("isom", 1000, 45_000)).andExpect(status().isOk())
			.andExpect(jsonPath("$.mediaType").value("VIDEO"))
			.andExpect(jsonPath("$.seconds").value(45))
			.andExpect(jsonPath("$.width").doesNotExist()));
		String pdf = keyOf(upload("fees.pdf", ascii("%PDF-1.7 school fee list")).andExpect(status().isOk())
			.andExpect(jsonPath("$.mediaType").value("DOCUMENT")));
		assertThat(pdf).endsWith(".pdf");
	}

	@Test
	void mediaOverLimitRefused() throws Exception {
		long before = filesInNotificationFolder();
		// A 6 MB picture: pictures may be 5 MB.
		upload("big.png", MediaFiles.padded(MediaFiles.png(10, 10), 6 * MB)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("MEDIA_TOO_BIG"))
			.andExpect(jsonPath("$.message").value("A picture can be 5 MB at most. Make it smaller on your phone."));
		// A 6 MB video is fine for size, so only the picture limit is 5 MB.
		keyOf(upload("ok.mp4", MediaFiles.padded(MediaFiles.mp4("isom", 1000, 1000), 6 * MB))
			.andExpect(status().isOk()));
		// Above 16 MB nothing is read at all.
		upload("huge.mp4", MediaFiles.padded(MediaFiles.mp4("isom", 1000, 1000), 16 * MB + 1))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("MEDIA_TOO_BIG"));
		// Only the one good video was written.
		assertThat(filesInNotificationFolder()).isEqualTo(before + 1);
	}

	@Test
	void theNameDoesNotDecideTheType() throws Exception {
		upload("poster.jpg", ascii("MZ this is a program")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("MEDIA_WRONG_TYPE"));
		// A PDF named like a picture is saved as what it really is.
		String key = keyOf(upload("poster.jpg", ascii("%PDF-1.4 ...")).andExpect(status().isOk())
			.andExpect(jsonPath("$.mediaType").value("DOCUMENT")));
		assertThat(key).endsWith(".pdf");
	}

	@Test
	void anEmptyFileIsAValidationError() throws Exception {
		upload("empty.png", new byte[0]).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$.fields.file").value("is empty"));
	}

	@Test
	void onlyTheOwnerMayUploadForNow() throws Exception {
		String office = tokenFor(addUser("+919812340002", Role.OFFICE_ADMIN));
		mockMvc.perform(multipart("/api/v1/notification-media")
			.file(new MockMultipartFile("file", "a.png", "image/png", MediaFiles.png(4, 4)))
			.header("Authorization", bearer(office)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("FORBIDDEN"));
	}

}
