package io.decisiontrace.spring.annotation;

import io.decisiontrace.core.model.ActorType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Decision {
    String decisionType();

    String actorId() default "";

    ActorType actorType() default ActorType.SYSTEM;

    String actorVersion() default "";

    String actorOrg() default "";
}
