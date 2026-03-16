package io.decisiontrace.samples.service.dto;

public record AuthRequest(String userId, String deviceId, String ipAddress) {
}
