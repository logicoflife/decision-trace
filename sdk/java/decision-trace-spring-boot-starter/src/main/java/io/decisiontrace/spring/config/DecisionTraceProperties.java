package io.decisiontrace.spring.config;

import io.decisiontrace.core.model.ActorType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "decision-trace")
public class DecisionTraceProperties {
    private String tenantId = "default-tenant";
    private String environment = "dev";
    private String actorId = "decision-trace-java";
    private ActorType actorType = ActorType.SYSTEM;
    private String actorVersion;
    private String actorOrg;
    private String traceIdHeader = "X-Decision-Trace-Trace-Id";
    private String parentDecisionIdHeader = "X-Decision-Trace-Parent-Decision-Id";
    private int ringBufferSize = 1024;
    private boolean validationEnabled;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public void setActorType(ActorType actorType) {
        this.actorType = actorType;
    }

    public String getActorVersion() {
        return actorVersion;
    }

    public void setActorVersion(String actorVersion) {
        this.actorVersion = actorVersion;
    }

    public String getActorOrg() {
        return actorOrg;
    }

    public void setActorOrg(String actorOrg) {
        this.actorOrg = actorOrg;
    }

    public String getTraceIdHeader() {
        return traceIdHeader;
    }

    public void setTraceIdHeader(String traceIdHeader) {
        this.traceIdHeader = traceIdHeader;
    }

    public String getParentDecisionIdHeader() {
        return parentDecisionIdHeader;
    }

    public void setParentDecisionIdHeader(String parentDecisionIdHeader) {
        this.parentDecisionIdHeader = parentDecisionIdHeader;
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public void setRingBufferSize(int ringBufferSize) {
        this.ringBufferSize = ringBufferSize;
    }

    public boolean isValidationEnabled() {
        return validationEnabled;
    }

    public void setValidationEnabled(boolean validationEnabled) {
        this.validationEnabled = validationEnabled;
    }
}
