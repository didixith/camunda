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

import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.response.FailJobResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class FailJobCommandImpl extends CommandWithVariables<FailJobCommandStep2>
    implements FailJobCommandStep1, FailJobCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;

  public FailJobCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final long key,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate) {
    super(jsonMapper);
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    builder = FailJobRequest.newBuilder();
    builder.setJobKey(key);
  }

  @Override
  public FailJobCommandStep2 retries(final int retries) {
    builder.setRetries(retries);
    return this;
  }

  @Override
  public FailJobCommandStep2 retryBackoff(final Duration backoffTimeout) {
    builder.setRetryBackOff(backoffTimeout.toMillis());
    return this;
  }

  @Override
  public FailJobCommandStep2 errorMessage(final String errorMsg) {
    builder.setErrorMessage(errorMsg);
    return this;
  }

  @Override
  public FailJobCommandStep2 setVariablesInternal(final String variables) {
    builder.setVariables(variables);
    return this;
  }

  @Override
  public FinalCommandStep<FailJobResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public CamundaFuture<FailJobResponse> send() {
    final FailJobRequest request = builder.build();

    final RetriableClientFutureImpl<FailJobResponse, GatewayOuterClass.FailJobResponse> future =
        new RetriableClientFutureImpl<>(
            FailJobResponseImpl::new,
            retryPredicate,
            streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final FailJobRequest request,
      final StreamObserver<GatewayOuterClass.FailJobResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .failJob(request, streamObserver);
  }
}
