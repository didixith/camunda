/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.operate.schema.templates.ListViewTemplate.*;
import static io.camunda.operate.schema.templates.ListViewTemplate.STATE;
import static io.camunda.operate.schema.templates.TemplateDescriptor.POSITION;
import static io.camunda.zeebe.operate.exporter.util.OperateExportUtil.tenantOrDefault;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.*;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListViewProcessInstanceFromProcessInstanceHandler
    implements ExportHandler<ProcessInstanceForListViewEntity, ProcessInstanceRecordValue> {

  protected static final int EMPTY_PARENT_PROCESS_INSTANCE_ID = -1;

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewProcessInstanceFromProcessInstanceHandler.class);

  private static final Set<String> PI_AND_AI_START_STATES = Set.of(ELEMENT_ACTIVATING.name());
  private static final Set<String> PI_AND_AI_FINISH_STATES =
      Set.of(ELEMENT_COMPLETED.name(), ELEMENT_TERMINATED.name());

  private final ListViewTemplate listViewTemplate;
  private final boolean concurrencyMode;
  private final OperationsManager operationsManager;
  private final ProcessCache processCache;
  private final ImportBatch importBatch;
  private final MetricsStore metricsStore;
  private final ListViewStore listViewStore;
  private final FlowNodeStore flowNodeStore;

  private final Map<String, Record<ProcessInstanceRecordValue>> recordsMap = new HashMap<>();

  public ListViewProcessInstanceFromProcessInstanceHandler(
      ListViewTemplate listViewTemplate,
      boolean concurrencyMode,
      OperationsManager operationsManager,
      ProcessCache processCache,
      ImportBatch importBatch,
      MetricsStore metricsStore,
      ListViewStore listViewStore,
      FlowNodeStore flowNodeStore) {
    this.listViewTemplate = listViewTemplate;
    this.concurrencyMode = concurrencyMode;
    this.operationsManager = operationsManager;
    this.processCache = processCache;
    this.importBatch = importBatch;
    this.metricsStore = metricsStore;
    this.listViewStore = listViewStore;
    this.flowNodeStore = flowNodeStore;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<ProcessInstanceForListViewEntity> getEntityType() {
    return ProcessInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<ProcessInstanceRecordValue> record) {
    final var intent = record.getIntent().name();
    return PI_AND_AI_START_STATES.contains(intent)
        || PI_AND_AI_FINISH_STATES.contains(intent)
        || ELEMENT_MIGRATED.name().equals(intent);
  }

  @Override
  public List<String> generateIds(Record<ProcessInstanceRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getProcessInstanceKey()));
  }

  @Override
  public ProcessInstanceForListViewEntity createNewEntity(String id) {
    return new ProcessInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      Record<ProcessInstanceRecordValue> record, ProcessInstanceForListViewEntity entity) {

    final var recordValue = record.getValue();
    if (isProcessEvent(recordValue)) {
      updateProcessInstance(entity, record);
      recordsMap.put(entity.getId(), record);
    }
  }

  @Override
  public void flush(
      ProcessInstanceForListViewEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {

    final String id = entity.getId();
    final Record<ProcessInstanceRecordValue> record = recordsMap.get(id);
    if (record != null) {
      final var recordValue = record.getValue();
      if (isProcessEvent(recordValue)) {
        // complete operation
        if (isProcessInstanceTerminated(record)) {
          // resolve corresponding operation
          operationsManager.completeOperation(
              null, record.getKey(), null, OperationType.CANCEL_PROCESS_INSTANCE, batchRequest);
        } else if (isProcessInstanceMigrated(record)) {
          // resolve corresponding operation
          operationsManager.completeOperation(
              null, record.getKey(), null, OperationType.MIGRATE_PROCESS_INSTANCE, batchRequest);
        }
        recordsMap.remove(id);

        final boolean isRootProcessInstance =
            recordValue.getParentProcessInstanceKey() == EMPTY_PARENT_PROCESS_INSTANCE_ID;
        if (isRootProcessInstance) {
          registerStartedRootProcessInstance(entity, batchRequest, entity.getStartDate());
        }
      }
    }

    final Map<String, Object> updateFields = new HashMap<>();
    if (entity.getStartDate() != null) {
      updateFields.put(ListViewTemplate.START_DATE, entity.getStartDate());
    }
    if (entity.getEndDate() != null) {
      updateFields.put(ListViewTemplate.END_DATE, entity.getEndDate());
    }
    updateFields.put(ListViewTemplate.PROCESS_NAME, entity.getProcessName());
    updateFields.put(ListViewTemplate.PROCESS_VERSION, entity.getProcessVersion());
    updateFields.put(ListViewTemplate.PROCESS_KEY, entity.getProcessDefinitionKey());
    updateFields.put(ListViewTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
    updateFields.put(POSITION, entity.getPosition());
    if (entity.getState() != null) {
      updateFields.put(ListViewTemplate.STATE, entity.getState());
    }

    if (concurrencyMode) {
      batchRequest.upsertWithScriptAndRouting(
          listViewTemplate.getFullQualifiedName(),
          entity.getId(),
          entity,
          getProcessInstanceScript(),
          updateFields,
          String.valueOf(entity.getProcessInstanceKey()));
    } else {
      batchRequest.upsert(
          listViewTemplate.getFullQualifiedName(), entity.getId(), entity, updateFields);
    }
  }

  @Override
  public String getIndexName() {
    return listViewTemplate.getFullQualifiedName();
  }

  private ProcessInstanceForListViewEntity updateProcessInstance(
      final ProcessInstanceForListViewEntity piEntity,
      final Record<ProcessInstanceRecordValue> record) {

    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();

    piEntity
        .setId(String.valueOf(recordValue.getProcessInstanceKey()))
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setKey(recordValue.getProcessInstanceKey())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setProcessVersion(recordValue.getVersion())
        .setProcessName(
            processCache.getProcessNameOrDefaultValue(
                piEntity.getProcessDefinitionKey(), recordValue.getBpmnProcessId()));

    final OffsetDateTime timestamp =
        DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));
    final boolean isRootProcessInstance =
        recordValue.getParentProcessInstanceKey() == EMPTY_PARENT_PROCESS_INSTANCE_ID;
    if (intentStr.equals(ELEMENT_COMPLETED.name()) || intentStr.equals(ELEMENT_TERMINATED.name())) {
      importBatch.incrementFinishedWiCount();
      piEntity.setEndDate(timestamp);
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        piEntity.setState(ProcessInstanceState.CANCELED);
      } else {
        piEntity.setState(ProcessInstanceState.COMPLETED);
      }
    } else if (intentStr.equals(ELEMENT_ACTIVATING.name())) {
      piEntity.setStartDate(timestamp).setState(ProcessInstanceState.ACTIVE);
    } else {
      piEntity.setState(ProcessInstanceState.ACTIVE);
    }
    // call activity related fields
    if (!isRootProcessInstance) {
      piEntity
          .setParentProcessInstanceKey(recordValue.getParentProcessInstanceKey())
          .setParentFlowNodeInstanceKey(recordValue.getParentElementInstanceKey());
      if (piEntity.getTreePath() == null) {
        final String treePath = getTreePathForCalledProcess(recordValue);
        piEntity.setTreePath(treePath);
      }
    }
    if (piEntity.getTreePath() == null) {
      final String treePath =
          new TreePath()
              .startTreePath(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()))
              .toString();
      piEntity.setTreePath(treePath);
    }
    return piEntity;
  }

  private void registerStartedRootProcessInstance(
      final ProcessInstanceForListViewEntity piEntity,
      final BatchRequest batchRequest,
      final OffsetDateTime timestamp)
      throws PersistenceException {
    final String processInstanceKey = String.valueOf(piEntity.getProcessInstanceKey());
    metricsStore.registerProcessInstanceStartEvent(
        processInstanceKey, piEntity.getTenantId(), timestamp, batchRequest);
  }

  private String getTreePathForCalledProcess(final ProcessInstanceRecordValue recordValue) {
    final String parentTreePath =
        listViewStore.findProcessInstanceTreePathFor(recordValue.getParentProcessInstanceKey());

    if (parentTreePath != null) {
      final String flowNodeInstanceId =
          ConversionUtils.toStringOrNull(recordValue.getParentElementInstanceKey());
      final String callActivityId = getCallActivityId(flowNodeInstanceId);
      final String treePath =
          new TreePath(parentTreePath)
              .appendEntries(
                  callActivityId,
                  flowNodeInstanceId,
                  ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()))
              .toString();

      return treePath;
    } else {
      LOGGER.warn(
          "Unable to find parent tree path for parent instance id "
              + recordValue.getParentProcessInstanceKey());
      final String treePath =
          new TreePath()
              .startTreePath(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()))
              .toString();

      return treePath;
    }
  }

  private String getCallActivityId(final String flowNodeInstanceId) {
    final String callActivityId =
        flowNodeStore.getFlowNodeIdByFlowNodeInstanceId(flowNodeInstanceId);
    return callActivityId;
  }

  private boolean isProcessEvent(final ProcessInstanceRecordValue recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(
      final ProcessInstanceRecordValue recordValue, final BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }

  private boolean isProcessInstanceTerminated(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent() == ELEMENT_TERMINATED;
  }

  private boolean isProcessInstanceMigrated(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent() == ELEMENT_MIGRATED;
  }

  private static String getProcessInstanceScript() {
    return String.format(
        "if (ctx._source.%s == null || ctx._source.%s < params.%s) { "
            + "ctx._source.%s = params.%s; " // position
            + "ctx._source.%s = params.%s; " // process name
            + "ctx._source.%s = params.%s; " // process version
            + "ctx._source.%s = params.%s; " // process key
            + "ctx._source.%s = params.%s; " // bpmnProcessId
            + "if (params.%s != null) { ctx._source.%s = params.%s; }" // start date
            + "if (params.%s != null) { ctx._source.%s = params.%s; }" // end date
            + "if (params.%s != null) { ctx._source.%s = params.%s; }" // state
            + "}",
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        PROCESS_NAME,
        PROCESS_NAME,
        PROCESS_VERSION,
        PROCESS_VERSION,
        PROCESS_KEY,
        PROCESS_KEY,
        BPMN_PROCESS_ID,
        BPMN_PROCESS_ID,
        START_DATE,
        START_DATE,
        START_DATE,
        END_DATE,
        END_DATE,
        END_DATE,
        STATE,
        STATE,
        STATE);
  }
}
