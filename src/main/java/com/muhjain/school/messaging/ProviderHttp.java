package com.muhjain.school.messaging;

import java.net.http.HttpClient;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** A {@link RestClient} with the 5 second timeout. A slow provider must not hang a login or the worker. */
public final class ProviderHttp {

	private ProviderHttp() {
	}

	public static RestClient client(ProviderProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(properties.timeout());
		return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
	}

}
