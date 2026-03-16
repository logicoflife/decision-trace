package io.decisiontrace.samples.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SampleServiceEndpoints {
    private final Environment environment;

    public SampleServiceEndpoints(Environment environment) {
        this.environment = environment;
    }

    public String local(String path) {
        int port = Integer.parseInt(environment.getRequiredProperty("local.server.port"));
        return "http://localhost:" + port + path;
    }
}
