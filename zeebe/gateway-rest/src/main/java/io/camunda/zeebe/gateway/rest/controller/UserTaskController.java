/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.UserTaskServices;
import io.camunda.service.search.query.UserTaskQuery;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.TenantAttributeHolder;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@ZeebeRestController
@RequestMapping(path = {"/v1", "/v2"})
public class UserTaskController {

  private final BrokerClient brokerClient;

  @Autowired private UserTaskServices userTaskServices;

  @Autowired
  public UserTaskController(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
  }

  @PostMapping(
      path = "/user-tasks/{userTaskKey}/completion",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> completeUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskCompletionRequest completionRequest) {

    return RequestMapper.toUserTaskCompletionRequest(completionRequest, userTaskKey)
        .fold(this::sendBrokerRequest, UserTaskController::handleRequestMappingError);
  }

  @PostMapping(
      path = "/user-tasks/{userTaskKey}/assignment",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> assignUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody final UserTaskAssignmentRequest assignmentRequest) {

    return RequestMapper.toUserTaskAssignmentRequest(assignmentRequest, userTaskKey)
        .fold(this::sendBrokerRequest, UserTaskController::handleRequestMappingError);
  }

  @DeleteMapping(path = "/user-tasks/{userTaskKey}/assignee")
  public CompletableFuture<ResponseEntity<Object>> unassignUserTask(
      @PathVariable final long userTaskKey) {

    return RequestMapper.toUserTaskUnassignmentRequest(userTaskKey)
        .fold(this::sendBrokerRequest, UserTaskController::handleRequestMappingError);
  }

  @PatchMapping(
      path = "/user-tasks/{userTaskKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> updateUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskUpdateRequest updateRequest) {

    return RequestMapper.toUserTaskUpdateRequest(updateRequest, userTaskKey)
        .fold(this::sendBrokerRequest, UserTaskController::handleRequestMappingError);
  }

  private CompletableFuture<ResponseEntity<Object>> sendBrokerRequest(
      final BrokerRequest<?> brokerRequest) {
    return brokerClient
        .sendRequest(brokerRequest)
        .handleAsync(
            (response, error) ->
                RestErrorMapper.getResponse(
                        response, error, UserTaskController::mapRejectionToProblem)
                    .orElseGet(() -> ResponseEntity.noContent().build()));
  }

  private static CompletableFuture<ResponseEntity<Object>> handleRequestMappingError(
      final ProblemDetail problemDetail) {
    return CompletableFuture.completedFuture(RestErrorMapper.mapProblemToResponse(problemDetail));
  }

  private static ProblemDetail mapRejectionToProblem(final BrokerRejection rejection) {
    final String message =
        String.format(
            "Command '%s' rejected with code '%s': %s",
            rejection.intent(), rejection.type(), rejection.reason());
    final String title = rejection.type().name();
    return switch (rejection.type()) {
      case NOT_FOUND:
        yield RestErrorMapper.createProblemDetail(HttpStatus.NOT_FOUND, message, title);
      case INVALID_STATE:
        yield RestErrorMapper.createProblemDetail(HttpStatus.CONFLICT, message, title);
      case INVALID_ARGUMENT:
      case ALREADY_EXISTS:
        yield RestErrorMapper.createProblemDetail(HttpStatus.BAD_REQUEST, message, title);
      default:
        {
          yield RestErrorMapper.createProblemDetail(
              HttpStatus.INTERNAL_SERVER_ERROR, message, title);
        }
    };
  }

  @PostMapping(
      path = "/user-tasks/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<UserTaskSearchQueryResponse> searchUserTasks(
      @RequestBody(required = false) final UserTaskSearchQueryRequest query) {
    return SearchQueryRequestMapper.toUserTaskQuery(query == null? new UserTaskSearchQueryRequest() : query)
        .fold(
            (Function<? super UserTaskQuery, ? extends ResponseEntity<UserTaskSearchQueryResponse>>)
                this::search,
            RestErrorMapper::mapProblemToResponse);
  }

  private ResponseEntity<UserTaskSearchQueryResponse> search(final UserTaskQuery query) {
    try {
      final var tenantIds = TenantAttributeHolder.tenantIds();
      final var result =
          userTaskServices.withAuthentication((a) -> a.tenants(tenantIds)).search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toUserTaskSearchQueryResponse(result).get());
    } catch (final Throwable e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST, e.getMessage(), "Failed to execute UserTask Search Query");
      return ResponseEntity.of(problemDetail)
          .headers(httpHeaders -> httpHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON))
          .build();
    }
  }
}
