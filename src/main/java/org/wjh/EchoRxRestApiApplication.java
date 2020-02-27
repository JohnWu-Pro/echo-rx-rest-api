package org.wjh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EchoRxRestApiApplication {

    public static void main(String[] args) {
        // Hooks.onOperatorDebug();
        SpringApplication.run(EchoRxRestApiApplication.class, args);
    }

}
