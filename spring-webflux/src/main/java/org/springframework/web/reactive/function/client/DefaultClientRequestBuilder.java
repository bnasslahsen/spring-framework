/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.reactive.function.client;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;

/**
 * Default implementation of {@link ClientRequest.Builder}.
 *
 * @author Arjen Poutsma
 * @since 5.0
 */
class DefaultClientRequestBuilder<T> implements ClientRequest.Builder<T> {

	private final HttpMethod method;

	private final URI url;

	private final HttpHeaders headers = new HttpHeaders();

	private final MultiValueMap<String, String> cookies = new LinkedMultiValueMap<>();

	private BodyInserter<T, ? super ClientHttpRequest> inserter = BodyInserters.empty();


	public DefaultClientRequestBuilder(HttpMethod method, URI url) {
		this.method = method;
		this.url = url;
	}

	@Override
	public ClientRequest.Builder<T> header(String headerName, String... headerValues) {
		for (String headerValue : headerValues) {
			this.headers.add(headerName, headerValue);
		}
		return this;
	}

	@Override
	public ClientRequest.Builder<T> headers(HttpHeaders headers) {
		if (headers != null) {
			this.headers.putAll(headers);
		}
		return this;
	}

	@Override
	public ClientRequest.Builder<T> cookie(String name, String value) {
		this.cookies.add(name, value);
		return this;
	}

	@Override
	public ClientRequest.Builder<T> cookies(MultiValueMap<String, String> cookies) {
		if (cookies != null) {
			this.cookies.putAll(cookies);
		}
		return this;
	}

	@Override
	public <S, P extends Publisher<S>> ClientRequest.Builder<P> body(P publisher,
			Class<S> elementClass) {
		Assert.notNull(publisher, "'publisher' must not be null");
		Assert.notNull(elementClass, "'elementClass' must not be null");

		this.inserter = BodyInserters.fromPublisher(publisher, elementClass);
	}

	@Override
	public ClientRequest.Builder<T> body(BodyInserter<T, ? super ClientHttpRequest> inserter) {
		this.inserter = inserter != null ? inserter : BodyInserters.empty();
		return this;
	}

	@Override
	public ClientRequest<T> build() {
		return new BodyInserterRequest<T>(this.method, this.url, this.headers, this.cookies,
				this.inserter);
	}

	private static class BodyInserterRequest<T> implements ClientRequest<T> {

		private final HttpMethod method;

		private final URI url;

		private final HttpHeaders headers;

		private final MultiValueMap<String, String> cookies;

		private final BodyInserter<T, ? super ClientHttpRequest> inserter;

		public BodyInserterRequest(HttpMethod method, URI url, HttpHeaders headers,
				MultiValueMap<String, String> cookies,
				BodyInserter<T, ? super ClientHttpRequest> inserter) {
			this.method = method;
			this.url = url;
			this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
			this.cookies = CollectionUtils.unmodifiableMultiValueMap(cookies);
			this.inserter = inserter;
		}

		@Override
		public HttpMethod method() {
			return this.method;
		}

		@Override
		public URI url() {
			return this.url;
		}

		@Override
		public HttpHeaders headers() {
			return this.headers;
		}

		@Override
		public MultiValueMap<String, String> cookies() {
			return this.cookies;
		}

		@Override
		public BodyInserter<T, ? super ClientHttpRequest> inserter() {
			return this.inserter;
		}

		@Override
		public Mono<Void> writeTo(ClientHttpRequest request, ExchangeStrategies strategies) {
			HttpHeaders requestHeaders = request.getHeaders();
			if (!this.headers.isEmpty()) {
				this.headers.entrySet().stream()
						.filter(entry -> !requestHeaders.containsKey(entry.getKey()))
						.forEach(entry -> requestHeaders
								.put(entry.getKey(), entry.getValue()));
			}
			MultiValueMap<String, HttpCookie> requestCookies = request.getCookies();
			if (!this.cookies.isEmpty()) {
				this.cookies.entrySet().forEach(entry -> {
					String name = entry.getKey();
					entry.getValue().forEach(value -> {
						HttpCookie cookie = new HttpCookie(name, value);
						requestCookies.add(name, cookie);
					});
				});
			}

			return this.inserter.insert(request, new BodyInserter.Context() {
				@Override
				public Supplier<Stream<HttpMessageWriter<?>>> messageWriters() {
					return strategies.messageWriters();
				}

				@Override
				public Map<String, Object> hints() {
					return Collections.emptyMap();
				}
			});
		}
	}
}
