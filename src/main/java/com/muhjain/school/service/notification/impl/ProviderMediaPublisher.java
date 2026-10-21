package com.muhjain.school.service.notification.impl;

import com.muhjain.school.exception.SmsSendException;
import com.muhjain.school.service.messaging.ProviderApi;
import com.muhjain.school.service.notification.MediaPublisher;
import com.muhjain.school.service.storage.FileStorage;
import com.muhjain.school.util.MediaRules;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * The real upload, used when {@code app.messaging.whatsapp-provider} is anything but {@code log}: reads the file from
 * our {@link FileStorage} and passes it to the one {@link ProviderApi} class. The provider gets only the file's random
 * name ({@code 9b1e.mp4}), never a person's name.
 */
@Component
@ConditionalOnExpression("!'${app.messaging.whatsapp-provider:log}'.trim().equalsIgnoreCase('log')")
public class ProviderMediaPublisher implements MediaPublisher {

	private final FileStorage storage;

	private final ObjectProvider<ProviderApi> api;

	public ProviderMediaPublisher(FileStorage storage, ObjectProvider<ProviderApi> api) {
		this.storage = storage;
		this.api = api;
	}

	@Override
	public String publish(String mediaKey, MediaRules.Format format) throws SmsSendException {
		ProviderApi provider = api.getIfAvailable();
		if (provider == null) {
			throw new SmsSendException("No provider class (ProviderApi) is installed");
		}
		byte[] bytes;
		try (InputStream in = storage.open(mediaKey)) {
			bytes = in.readAllBytes();
		}
		catch (IOException ex) {
			throw new SmsSendException("The file of this notification could not be read", ex);
		}
		String fileName = mediaKey.substring(mediaKey.lastIndexOf('/') + 1);
		return provider.uploadMedia(bytes, format.contentType(), fileName);
	}

}
