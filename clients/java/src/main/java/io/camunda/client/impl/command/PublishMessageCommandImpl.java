/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.command;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.PublishMessageCommandStep1;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.response.PublishMessageResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class PublishMessageCommandImpl extends CommandWithVariables<PublishMessageCommandImpl>
    implements PublishMessageCommandStep1, PublishMessageCommandStep2, PublishMessageCommandStep3 {

  private final GatewayStub asyncStub;
  private final Predicate<StatusCode> retryPredicate;
  private final PublishMessageRequest.Builder builder;
  private Duration requestTimeout;

  public PublishMessageCommandImpl(
      final GatewayStub asyncStub,
      final CamundaClientConfiguration configuration,
      final JsonMapper jsonMapper,
      final Predicate<StatusCode> retryPredicate) {
    super(jsonMapper);
    this.asyncStub = asyncStub;
    this.retryPredicate = retryPredicate;
    builder = PublishMessageRequest.newBuilder();
    requestTimeout = configuration.getDefaultRequestTimeout();
    builder.setTimeToLive(configuration.getDefaultMessageTimeToLive().toMillis());
    tenantId(configuration.getDefaultTenantId());
  }

  @Override
  protected PublishMessageCommandImpl setVariablesInternal(final String variables) {
    builder.setVariables(variables);
    return this;
  }

  @Override
  public PublishMessageCommandStep3 messageId(final String messageId) {
    builder.setMessageId(messageId);
    return this;
  }

  @Override
  public PublishMessageCommandStep3 timeToLive(final Duration timeToLive) {
    builder.setTimeToLive(timeToLive.toMillis());
    return this;
  }

  @Override
  public PublishMessageCommandStep3 correlationKey(final String correlationKey) {
    builder.setCorrelationKey(correlationKey);
    return this;
  }

  @Override
  public PublishMessageCommandStep3 withoutCorrelationKey() {
    return this;
  }

  @Override
  public PublishMessageCommandStep2 messageName(final String messageName) {
    builder.setName(messageName);
    return this;
  }

  @Override
  public PublishMessageCommandStep3 tenantId(final String tenantId) {
    builder.setTenantId(tenantId);
    return this;
  }

  @Override
  public FinalCommandStep<PublishMessageResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public CamundaFuture<PublishMessageResponse> send() {
    final PublishMessageRequest request = builder.build();
    final RetriableClientFutureImpl<
            PublishMessageResponse, GatewayOuterClass.PublishMessageResponse>
        future =
            new RetriableClientFutureImpl<>(
                PublishMessageResponseImpl::new,
                retryPredicate,
                streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final PublishMessageRequest request,
      final StreamObserver<GatewayOuterClass.PublishMessageResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .publishMessage(request, streamObserver);
  }
}
