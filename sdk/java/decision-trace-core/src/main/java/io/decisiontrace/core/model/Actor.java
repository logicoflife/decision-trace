package io.decisiontrace.core.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Actor {
    private final String id;
    private final ActorType type;
    private final String version;
    private final String org;

    public Actor(String id, ActorType type, String version, String org) {
        this.id = requireNonBlank(id, "actor.id");
        this.type = Objects.requireNonNull(type, "actor.type");
        this.version = version;
        this.org = org;
    }

    public static Actor of(String id, ActorType type) {
        return new Actor(id, type, null, null);
    }

    public String id() {
        return id;
    }

    public ActorType type() {
        return type;
    }

    public String version() {
        return version;
    }

    public String org() {
        return org;
    }

    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("type", type.wireValue());
        if (version != null) {
            map.put("version", version);
        }
        if (org != null) {
            map.put("org", org);
        }
        return map;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
