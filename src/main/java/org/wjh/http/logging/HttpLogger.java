package org.wjh.http.logging;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import reactor.core.publisher.Mono;

public interface HttpLogger {

    boolean shouldLog(HttpMethod method, URI uri);

    /**
     * @param dir
     * @param httpMethod
     * @param url
     * @param headers
     * @param body
     *            the request body, will be logged if and only if it is not {@code null}
     */
    void logRequest(MessageDirection dir, String httpMethod, URI url, HttpHeaders headers, byte[] body);

    boolean shouldLogRequestBody(HttpMethod method, HttpHeaders headers);

    /**
     * @param dir
     * @param statusCode
     * @param statusText
     * @param headers
     * @param body
     *            the response body, will be logged if and only if it is not {@code null}
     */
    void logResponse(MessageDirection dir, int statusCode, String statusText, HttpHeaders headers, byte[] body);

    boolean shouldLogResponseBody(int statusCode, HttpHeaders headers);

    enum MessageDirection {
        Inbound, Outbound
    }

    Mono<byte[]> EMPTY_BODY_MONO = Mono.just(new byte[0]).cache();
}
