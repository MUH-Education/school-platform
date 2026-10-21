package com.muhjain.school.notification;

import com.muhjain.school.model.enums.MediaFileType;
import com.muhjain.school.util.MediaRules;
import com.muhjain.school.util.MediaRules.Format;
import org.junit.jupiter.api.Test;

import static com.muhjain.school.notification.MediaFiles.ascii;
import static org.assertj.core.api.Assertions.assertThat;

/** The file type comes from the first bytes, never from the name. */
class MediaRulesTest {

	@Test
	void everyAcceptedFormatIsKnownByItsFirstBytes() throws Exception {
		assertThat(MediaRules.detect(MediaFiles.png(4, 4))).contains(Format.PNG);
		assertThat(MediaRules.detect(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0 })).contains(Format.JPEG);
		assertThat(MediaRules.detect(ascii("%PDF-1.7 ..."))).contains(Format.PDF);
		assertThat(MediaRules.detect(MediaFiles.mp4("isom", 1000, 1000))).contains(Format.MP4);
		assertThat(MediaRules.detect(MediaFiles.mp4("M4A ", 1000, 1000))).contains(Format.M4A);
		assertThat(MediaRules.detect(ascii("OggS...."))).contains(Format.OGG);
		assertThat(MediaRules.detect(ascii("#!AMR\n.."))).contains(Format.AMR);
		assertThat(MediaRules.detect(ascii("ID3....."))).contains(Format.MP3);
		assertThat(MediaRules.detect(new byte[] { (byte) 0xFF, (byte) 0xFB, 0x50, 0 })).contains(Format.MP3);
	}

	@Test
	void otherFilesAreRefused() {
		assertThat(MediaRules.detect(ascii("MZ program"))).isEmpty();          // a Windows program
		assertThat(MediaRules.detect(ascii("PK\u0003\u0004 zip"))).isEmpty();  // a zip or Word file
		assertThat(MediaRules.detect(ascii("hello"))).isEmpty();
		assertThat(MediaRules.detect(new byte[0])).isEmpty();
	}

	@Test
	void limitsByType() {
		assertThat(MediaRules.maxBytes(MediaFileType.IMAGE)).isEqualTo(5 * 1024 * 1024);
		assertThat(MediaRules.maxBytes(MediaFileType.DOCUMENT)).isEqualTo(5 * 1024 * 1024);
		assertThat(MediaRules.maxBytes(MediaFileType.VIDEO)).isEqualTo(16 * 1024 * 1024);
		assertThat(MediaRules.maxBytes(MediaFileType.AUDIO)).isEqualTo(16 * 1024 * 1024);
		assertThat(MediaRules.tooBigMessage(MediaFileType.VIDEO))
			.isEqualTo("A video can be 16 MB at most. Make it smaller on your phone.");
	}

	@Test
	void sizeOfAPictureAndLengthOfAVideo() throws Exception {
		assertThat(MediaRules.imageSize(MediaFiles.png(300, 200))).hasValueSatisfying(s -> {
			assertThat(s[0]).isEqualTo(300);
			assertThat(s[1]).isEqualTo(200);
		});
		assertThat(MediaRules.mp4Seconds(MediaFiles.mp4("isom", 1000, 45_000))).contains(45);
		assertThat(MediaRules.mp4Seconds(MediaFiles.mp4("isom", 600, 1_000))).contains(2);  // 1.67 s → 2
		// A broken or cut file gives no length, never an error.
		assertThat(MediaRules.mp4Seconds(MediaFiles.padded(MediaFiles.mp4("isom", 1000, 1000), 20))).isEmpty();
		assertThat(MediaRules.mp4Seconds(ascii("%PDF-1.7"))).isEmpty();
		assertThat(MediaRules.imageSize(ascii("not a picture"))).isEmpty();
	}

}
