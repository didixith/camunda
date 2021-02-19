/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.entity;

import static io.zeebe.tasklist.util.CollectionUtil.toArrayOfStrings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.entities.TaskState;
import java.util.Arrays;
import java.util.Objects;

public final class TaskDTO {

  private String id;
  private String workflowInstanceId;
  /** Field is used to resolve task name. */
  private String flowNodeBpmnId;

  private String flowNodeInstanceId;
  /** Field is used to resolve workflow name. */
  private String workflowId;
  /** Fallback value for workflow name. */
  private String bpmnProcessId;

  private String creationTime;
  private String completionTime;
  /** Field is used to return user data. */
  private String assigneeUsername;

  private TaskState taskState;

  private String[] sortValues;

  private boolean isFirst = false;

  public String getId() {
    return id;
  }

  public TaskDTO setId(String id) {
    this.id = id;
    return this;
  }

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public TaskDTO setWorkflowInstanceId(final String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
    return this;
  }

  public String getFlowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public TaskDTO setFlowNodeBpmnId(String flowNodeBpmnId) {
    this.flowNodeBpmnId = flowNodeBpmnId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public TaskDTO setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public TaskDTO setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskDTO setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getCreationTime() {
    return creationTime;
  }

  public TaskDTO setCreationTime(String creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public String getCompletionTime() {
    return completionTime;
  }

  public TaskDTO setCompletionTime(String completionTime) {
    this.completionTime = completionTime;
    return this;
  }

  public String getAssigneeUsername() {
    return assigneeUsername;
  }

  public TaskDTO setAssigneeUsername(String assigneeUsername) {
    this.assigneeUsername = assigneeUsername;
    return this;
  }

  public TaskState getTaskState() {
    return taskState;
  }

  public TaskDTO setTaskState(TaskState taskState) {
    this.taskState = taskState;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public TaskDTO setSortValues(final String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public boolean getIsFirst() {
    return isFirst;
  }

  public TaskDTO setIsFirst(final boolean first) {
    isFirst = first;
    return this;
  }

  public static TaskDTO createFrom(TaskEntity taskEntity, ObjectMapper objectMapper) {
    return createFrom(taskEntity, null, objectMapper);
  }

  public static TaskDTO createFrom(
      TaskEntity taskEntity, Object[] sortValues, ObjectMapper objectMapper) {
    final TaskDTO taskDTO =
        new TaskDTO()
            .setCreationTime(objectMapper.convertValue(taskEntity.getCreationTime(), String.class))
            .setCompletionTime(
                objectMapper.convertValue(taskEntity.getCompletionTime(), String.class))
            .setId(taskEntity.getId())
            .setWorkflowInstanceId(taskEntity.getWorkflowInstanceId())
            .setTaskState(taskEntity.getState())
            .setAssigneeUsername(taskEntity.getAssignee())
            .setBpmnProcessId(taskEntity.getBpmnProcessId())
            .setWorkflowId(taskEntity.getWorkflowId())
            .setFlowNodeBpmnId(taskEntity.getFlowNodeBpmnId())
            .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId());
    if (sortValues != null) {
      taskDTO.setSortValues(toArrayOfStrings(sortValues));
    }
    return taskDTO;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TaskDTO taskDTO = (TaskDTO) o;
    return isFirst == taskDTO.isFirst
        && Objects.equals(id, taskDTO.id)
        && Objects.equals(workflowInstanceId, taskDTO.workflowInstanceId)
        && Objects.equals(flowNodeBpmnId, taskDTO.flowNodeBpmnId)
        && Objects.equals(flowNodeInstanceId, taskDTO.flowNodeInstanceId)
        && Objects.equals(workflowId, taskDTO.workflowId)
        && Objects.equals(bpmnProcessId, taskDTO.bpmnProcessId)
        && Objects.equals(creationTime, taskDTO.creationTime)
        && Objects.equals(completionTime, taskDTO.completionTime)
        && Objects.equals(assigneeUsername, taskDTO.assigneeUsername)
        && taskState == taskDTO.taskState
        && Arrays.equals(sortValues, taskDTO.sortValues);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id,
            workflowInstanceId,
            flowNodeBpmnId,
            flowNodeInstanceId,
            workflowId,
            bpmnProcessId,
            creationTime,
            completionTime,
            assigneeUsername,
            taskState,
            isFirst);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public String toString() {
    return "TaskDTO{"
        + "id='"
        + id
        + '\''
        + ", workflowInstanceId='"
        + workflowInstanceId
        + '\''
        + ", flowNodeBpmnId='"
        + flowNodeBpmnId
        + '\''
        + ", flowNodeInstanceId='"
        + flowNodeInstanceId
        + '\''
        + ", workflowId='"
        + workflowId
        + '\''
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", creationTime='"
        + creationTime
        + '\''
        + ", completionTime='"
        + completionTime
        + '\''
        + ", assigneeUsername='"
        + assigneeUsername
        + '\''
        + ", taskState="
        + taskState
        + ", sortValues="
        + Arrays.toString(sortValues)
        + ", isFirst="
        + isFirst
        + '}';
  }
}
