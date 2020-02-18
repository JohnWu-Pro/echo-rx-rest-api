package org.wjh.service;

import reactor.core.publisher.Mono;

public interface MessageService {

    Mono<String> get(String input);
    Mono<String> post(String input);
}
