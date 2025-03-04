package io.yupiik.metrics.metricsscrapper.test;

import io.yupiik.fusion.testing.MonoFusionSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
@ExtendWith({
  SetEnvironment.class
  // add your other extensions there to ensure they are available for all tests
})
@MonoFusionSupport
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public @interface ApplicationSupport {
}
