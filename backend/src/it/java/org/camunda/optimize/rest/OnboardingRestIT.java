/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.OnboardingStateDto;
import org.camunda.optimize.dto.optimize.rest.OnboardingStateRestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Optional;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ONBOARDING_INDEX_NAME;

public class OnboardingRestIT extends AbstractIT {

  private static final String KEY_WHATSNEW = "whatsnew";

  @Test
  public void testGetOnboardingState() {
    // given
    assertThat(getOnboardingStateFromElasticsearch(DEFAULT_USERNAME, KEY_WHATSNEW)).isEmpty();

    // when
    final OnboardingStateRestDto onboardingStateRestDto = getOnboardingState(KEY_WHATSNEW);

    // then
    assertThat(onboardingStateRestDto.isSeen()).isEqualTo(false);
  }

  @Test
  public void testGetOnboardingState_invalidKey() {
    // given
    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetOnboardingStateForKey("invalid")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  public void testGetOnboardingState_unauthorized() {
    // given
    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetOnboardingStateForKey(KEY_WHATSNEW)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(SC_UNAUTHORIZED);
  }

  @Test
  public void testSetOnboardingState() {
    // given
    assertThat(getOnboardingStateFromElasticsearch(DEFAULT_USERNAME, KEY_WHATSNEW)).isEmpty();

    // when
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildSetOnboardingStateForKey(KEY_WHATSNEW, true)
      .execute(204);

    // then
    final OnboardingStateRestDto onboardingStateRestDto = getOnboardingState(KEY_WHATSNEW);
    assertThat(onboardingStateRestDto.isSeen()).isEqualTo(true);
    final Optional<OnboardingStateDto> stateFromElasticsearch = getOnboardingStateFromElasticsearch(
      DEFAULT_USERNAME, KEY_WHATSNEW
    );
    assertThat(stateFromElasticsearch)
      .get()
      .hasFieldOrPropertyWithValue(OnboardingStateDto.Fields.key, KEY_WHATSNEW)
      .hasFieldOrPropertyWithValue(OnboardingStateDto.Fields.userId, DEFAULT_USERNAME)
      .hasFieldOrPropertyWithValue(OnboardingStateDto.Fields.seen, true);
  }

  @Test
  public void testSetOnboardingState_invalidKey() {
    // given
    final String invalidKey = "invalid";
    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildSetOnboardingStateForKey(invalidKey, true)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(SC_NOT_FOUND);
    assertThat(getOnboardingStateFromElasticsearch(DEFAULT_USERNAME, invalidKey)).isEmpty();
  }

  @Test
  public void testSetOnboardingState_unauthorized() {
    // given
    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildSetOnboardingStateForKey(KEY_WHATSNEW, true)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(SC_UNAUTHORIZED);
  }

  private OnboardingStateRestDto getOnboardingState(final String key) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetOnboardingStateForKey(key)
      .execute(OnboardingStateRestDto.class, 200);
  }

  @SneakyThrows
  private Optional<OnboardingStateDto> getOnboardingStateFromElasticsearch(final String userId, final String key) {
    final GetResponse getResponse = elasticSearchIntegrationTestExtension
      .getOptimizeElasticClient()
      .get(
        new GetRequest(ONBOARDING_INDEX_NAME).id(userId + ":" + key),
        RequestOptions.DEFAULT
      );
    return Optional.ofNullable(getResponse.getSourceAsString())
      .map(json -> {
        try {
          return embeddedOptimizeExtension.getObjectMapper().readValue(json, OnboardingStateDto.class);
        } catch (IOException e) {
          throw new OptimizeIntegrationTestException("Failed parsing response: " + json);
        }
      });
  }
}
