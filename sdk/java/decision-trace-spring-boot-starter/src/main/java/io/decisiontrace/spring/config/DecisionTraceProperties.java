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
    private String collectorEndpoint;
    private int collectorBatchSize = 50;
    private long collectorConnectTimeoutMillis = 1000L;
    private long collectorRequestTimeoutMillis = 3000L;
    private String jsonLedgerPath;
    private boolean otelExportEnabled = true;

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

    public String getCollectorEndpoint() {
        return collectorEndpoint;
    }

    public void setCollectorEndpoint(String collectorEndpoint) {
        this.collectorEndpoint = collectorEndpoint;
    }

    public int getCollectorBatchSize() {
        return collectorBatchSize;
    }

    public void setCollectorBatchSize(int collectorBatchSize) {
        this.collectorBatchSize = collectorBatchSize;
    }

    public long getCollectorConnectTimeoutMillis() {
        return collectorConnectTimeoutMillis;
    }

    public void setCollectorConnectTimeoutMillis(long collectorConnectTimeoutMillis) {
        this.collectorConnectTimeoutMillis = collectorConnectTimeoutMillis;
    }

    public long getCollectorRequestTimeoutMillis() {
        return collectorRequestTimeoutMillis;
    }

    public void setCollectorRequestTimeoutMillis(long collectorRequestTimeoutMillis) {
        this.collectorRequestTimeoutMillis = collectorRequestTimeoutMillis;
    }

    public String getJsonLedgerPath() {
        return jsonLedgerPath;
    }

    public void setJsonLedgerPath(String jsonLedgerPath) {
        this.jsonLedgerPath = jsonLedgerPath;
    }

    public boolean isOtelExportEnabled() {
        return otelExportEnabled;
    }

    public void setOtelExportEnabled(boolean otelExportEnabled) {
        this.otelExportEnabled = otelExportEnabled;
    }
}
