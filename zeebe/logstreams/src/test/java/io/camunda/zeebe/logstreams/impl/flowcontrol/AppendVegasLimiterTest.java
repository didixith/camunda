/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.concurrency.limits.limit.AbstractLimit;
import org.junit.Test;

public final class AppendVegasLimiterTest {

  @Test
  public void shouldUseDefaultValues() {
    // given - when
    final BackpressureCfgVegas vegasCfg = new BackpressureCfgVegas();

    // then
    assertThat(vegasCfg.getAlphaLimit()).isEqualTo(0.7);
    assertThat(vegasCfg.getBetaLimit()).isEqualTo(0.95);
    assertThat(vegasCfg.getInitialLimit()).isEqualTo(1024);
    assertThat(vegasCfg.getMaxConcurrency()).isEqualTo(1024 * 32);
  }

  @Test
  public void shouldBuild() {
    // given
    final BackpressureCfgVegas vegasCfg = new BackpressureCfgVegas();

    // when
    final AbstractLimit abstractLimit = vegasCfg.get();

    // then
    assertThat(abstractLimit.getLimit()).isEqualTo(1024);
  }
}
