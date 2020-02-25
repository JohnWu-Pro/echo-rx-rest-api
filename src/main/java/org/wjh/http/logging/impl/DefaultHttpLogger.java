package org.wjh.http.logging.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.wjh.http.logging.HttpLogger;

@Service
public class DefaultHttpLogger implements HttpLogger {

    private static final Logger logger = LoggerFactory.getLogger(DefaultHttpLogger.class);

    private static final String HTTP_VERSION = "HTTP/1.1";
    private static final String SPACE = " ";
    private static final String COLON = ": ";
    private static final String INDENT = "\t";
    private static final String NEW_LINE = "\n";

    private static final Pattern TEXT_SUBTYPE_PATTERNS = Pattern.compile(//@formatter:off
            "^"
            + "(?:xml)"
            + "|(?:.+\\+xml)"
            + "|(?:json)"
            + "|(?:.+\\+json)"
            + "|(?:x-www-form-urlencoded)"
            + "$");//@formatter:on

    @Override
    public boolean shouldLog(HttpMethod method, URI uri) {
        return true; // TODO
    }

    @Override
    public boolean shouldLogRequestBody(HttpMethod method, HttpHeaders headers) {
        return hasRequestBody(method, headers) && isTextBody(headers);
    }

    private boolean hasRequestBody(HttpMethod method, HttpHeaders headers) {
        switch (method) {
        case GET:
        case HEAD:
        case OPTIONS:
        case TRACE:
            return false;
        case PATCH:
        case POST:
        case PUT:
        case DELETE:
            return notZeroLength(headers);
        }
        return false;
    }

    private boolean notZeroLength(HttpHeaders headers) {
        return headers.getContentLength() != 0; // -1 (not specified, unknown), OR, > 0 (has known length)
    }

    @Override
    public boolean shouldLogResponseBody(int statusCode, HttpHeaders headers) {
        return (400 <= statusCode && statusCode <= 599) || (notZeroLength(headers) && isTextBody(headers));
    }

    private boolean isTextBody(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        if (contentType != null) {
            if ("text".equals(contentType.getType())) {
                return true;
            }
            String subtype = contentType.getSubtype();
            if (subtype != null) {
                return TEXT_SUBTYPE_PATTERNS.matcher(subtype).matches();
            }
        }
        return false;
    }

    @Override
    public void logRequest(MessageDirection dir, String httpMethod, URI url, HttpHeaders headers, byte[] body) {
        StringBuilder builder = new StringBuilder(dir.name()).append(" HTTP Request:").append(NEW_LINE);

        // Start line::<method> <URL> HTTP/<version>
        builder.append(INDENT).append(httpMethod).append(SPACE).append(url).append(SPACE).append(HTTP_VERSION).append(NEW_LINE);

        appendHeadersAndBody(builder, headers, body);

        logger.info(builder.toString());
    }

    @Override
    public void logResponse(MessageDirection dir, int statusCode, String statusText, HttpHeaders headers, byte[] body) {
        StringBuilder builder = new StringBuilder(dir.name()).append(" HTTP Response:").append(NEW_LINE);

        // Status line::HTTP/<version> <status code> <status text>
        builder.append(INDENT).append(HTTP_VERSION).append(SPACE).append(statusCode).append(text(statusCode, statusText)).append(NEW_LINE);

        appendHeadersAndBody(builder, headers, body);

        logger.info(builder.toString());
    }

    private String text(int statusCode, String statusText) {
        if (statusText == null || statusText.isEmpty()) {
            HttpStatus status = HttpStatus.resolve(statusCode);
            statusText = status == null ? null : status.getReasonPhrase();
        }
        return (statusText == null || statusText.isEmpty()) ? "" : SPACE + statusText;
    }

    private void appendHeadersAndBody(StringBuilder builder, HttpHeaders headers, byte[] body) {
        // HTTP headers::one single line for each header
        // <header name>: <header values>
        headers.forEach((String name, List<String> values) -> {
            builder.append(INDENT).append(name).append(COLON).append(String.join(",", values)).append(NEW_LINE);
        });

        // Body::optional
        if (!isEmpty(body)) {
            builder.append(NEW_LINE).append(INDENT).append(new String(body, determineCharset(headers))).append(NEW_LINE);
        }
    }

    private boolean isEmpty(byte[] bytes) {
        return bytes == null || bytes.length == 0;
    }

    private Charset determineCharset(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        if (contentType != null) {
            try {
                Charset charset = contentType.getCharset();
                if (charset != null) {
                    return charset;
                }
            } catch (UnsupportedCharsetException e) {
                // ignore
            }
        }
        return UTF_8;
    }
}
