package org.wjh.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wjh.service.MessageService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/echo")
public class EchoApi {

    private static final Logger logger = LoggerFactory.getLogger(EchoApi.class);

    @Autowired
    private MessageService messageService;

    @GetMapping
    public Mono<String> get(@RequestParam(name = "input") String input) {
        logger.trace("Calling get({}) ...", input);

        return messageService.get(input);
    }

    // @PostMapping
    // public Mono<String> post(@RequestBody Mono<Form> formMono) {
    // logger.trace("Calling post({}) ...", formMono);
    //
    // return formMono.map(form -> messageService.post(form.getInput()));
    // }

    // @PostMapping
    // public Mono<String> post(@RequestBody Mono<MultiValueMap<String, String>> paramsMono) {
    // logger.trace("Calling post({}) ...", paramsMono);
    //
    // return paramsMono.map(params -> messageService.post(params.getFirst("input")));
    // }

    // @PostMapping(produces = APPLICATION_JSON_VALUE)
    // public Mono<Map<String, Object>> post(ServerWebExchange exchange, @RequestBody(required = false) String body) throws IOException {
    // HashMap<String, Object> ret = new HashMap<>();
    // ret.put("headers", exchange.getRequest().getHeaders());
    // ret.put("data", body);
    // HashMap<String, Object> form = new HashMap<>();
    // ret.put("form", form);
    // return exchange.getFormData().flatMap(map -> {
    // for (Map.Entry<String, List<String>> entry : map.entrySet()) {
    // for (String value : entry.getValue()) {
    // form.put(entry.getKey(), value);
    // }
    // }
    // return Mono.just(ret);
    // });
    // }

    @PostMapping
    public Mono<String> post(Mono<Form> formMono) {
        logger.trace("Calling post({}) ...", formMono);

        return formMono.flatMap(form -> messageService.post(form.getInput()));
    }

    public static class Form {
        private String input;

        public String getInput() {
            return input;
        }

        public void setInput(String input) {
            this.input = input;
        }
    }
}
