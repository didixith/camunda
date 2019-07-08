/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Predicate;

import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.es.reader.ActivityInstanceReader;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.rest.dto.activity.ActivityInstanceDto;
import org.camunda.operate.rest.dto.activity.ActivityInstanceTreeDto;
import org.camunda.operate.rest.dto.activity.ActivityInstanceTreeRequestDto;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.rest.dto.listview.VariablesQueryDto;
import org.camunda.operate.rest.dto.listview.WorkflowInstanceStateDto;
import org.camunda.operate.rest.exception.NotFoundException;
import org.camunda.operate.util.IdTestUtil;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.StringUtils;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;

public class ImportIT extends OperateZeebeIntegrationTest {

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private ActivityInstanceReader activityInstanceReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  @Qualifier("activityIsTerminatedCheck")
  private Predicate<Object[]> activityIsTerminatedCheck;

  @Autowired
  @Qualifier("activityIsActiveCheck")
  private Predicate<Object[]> activityIsActiveCheck;

  @Autowired
  @Qualifier("activityIsCompletedCheck")
  private Predicate<Object[]> activityIsCompletedCheck;

  @Autowired
  @Qualifier("incidentIsActiveCheck")
  private Predicate<Object[]> incidentIsActiveCheck;

  @Autowired
  @Qualifier("incidentIsResolvedCheck")
  private Predicate<Object[]> incidentIsResolvedCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCompletedCheck")
  private Predicate<Object[]> workflowInstanceIsCompletedCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCanceledCheck")
  private Predicate<Object[]> workflowInstanceIsCanceledCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCreatedCheck")
  private Predicate<Object[]> workflowInstanceIsCreatedCheck;

  @Autowired
  @Qualifier("variableExistsCheck")
  private Predicate<Object[]> variableExistsCheck;
  
  @Autowired
  @Qualifier("variableEqualsCheck")
  private Predicate<Object[]> variableEqualsCheck;

  private ZeebeClient zeebeClient;

  private OffsetDateTime testStartTime;

  @Before
  public void init() {
    super.before();
    testStartTime = OffsetDateTime.now();
    zeebeClient = super.getClient();
  }

  @After
  public void after() {
    super.after();
  }

  @Test
  public void testWorkflowInstanceCreated() {
    // having
    String processId = "demoProcess";
    final Long workflowId = deployWorkflow("demoProcess_v_1.bpmn");

    //when
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskA");

    //then
    final Long workflowInstanceId = workflowInstanceKey;
    final WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
    assertThat(workflowInstanceEntity.getWorkflowId()).isEqualTo(workflowId);
    assertThat(workflowInstanceEntity.getWorkflowName()).isEqualTo("Demo process");
    assertThat(workflowInstanceEntity.getWorkflowVersion()).isEqualTo(1);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(workflowInstanceId.toString());
    assertThat(workflowInstanceEntity.getKey()).isEqualTo(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
    assertThat(workflowInstanceEntity.getEndDate()).isNull();
    assertThat(workflowInstanceEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getWorkflowId()).isEqualTo(StringUtils.toStringOrNull(workflowId));
    assertThat(wi.getWorkflowName()).isEqualTo("Demo process");
    assertThat(wi.getWorkflowVersion()).isEqualTo(1);
    assertThat(wi.getId()).isEqualTo(workflowInstanceId.toString());
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.ACTIVE);
    assertThat(wi.getEndDate()).isNull();
    assertThat(wi.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(wi.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    assertStartActivityCompleted(tree.getChildren().get(0));
    assertActivityIsActive(tree.getChildren().get(1), "taskA");
  }

  @Test
  public void testVariablesAreLoaded() {
    // having
    String processId = "demoProcess";
    /*final String workflowId =*/ deployWorkflow("demoProcess_v_1.bpmn");

    //when TC 1
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskA");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, workflowInstanceKey, workflowInstanceKey, "a");

    //then we can find the instance by 2 variable values: a = b, foo = b
    assertVariableExists(workflowInstanceKey, "a", "\"b\"");
    assertVariableExists(workflowInstanceKey, "foo", "\"b\"");
    assertVariableDoesNotExist(workflowInstanceKey, "a", "\"c\"");

    //when TC 2
    //update variable
    ZeebeTestUtil.updateVariables(zeebeClient, workflowInstanceKey, "{\"a\": \"c\"}");
    //elasticsearchTestRule.processAllEvents(2, ImportValueType.VARIABLE);
    elasticsearchTestRule.processAllRecordsAndWait(variableEqualsCheck, workflowInstanceKey,workflowInstanceKey,"a","\"c\"");
    //then we can find the instance by 2 variable values: foo = b
    assertVariableDoesNotExist(workflowInstanceKey, "a", "\"b\"");
    assertVariableExists(workflowInstanceKey, "foo", "\"b\"");
    assertVariableExists(workflowInstanceKey, "a", "\"c\"");
  }

  private void assertVariableExists(long workflowInstanceKey, String name, String value) {
    ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView(
      TestUtil.createGetAllWorkflowInstancesQuery(q -> {
        q.setVariable(new VariablesQueryDto(name, value));
      }));
    assertThat(wi.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
  }

  private void assertVariableDoesNotExist(long workflowInstanceKey, String name, String value) {
    final ListViewResponseDto listViewResponse = listViewReader.queryWorkflowInstances(TestUtil.createGetAllWorkflowInstancesQuery(q ->
      q.setVariable(new VariablesQueryDto(name, value))), 0, 100);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(0);
    assertThat(listViewResponse.getWorkflowInstances()).hasSize(0);
  }

  private ActivityInstanceTreeDto getActivityInstanceTree(long workflowInstanceKey) {
    return activityInstanceReader.getActivityInstanceTree(new ActivityInstanceTreeRequestDto(IdTestUtil.getId(workflowInstanceKey)));
  }

  private ListViewWorkflowInstanceDto getSingleWorkflowInstanceForListView(ListViewRequestDto request) {
    final ListViewResponseDto listViewResponse = listViewReader.queryWorkflowInstances(request, 0, 100);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(1);
    assertThat(listViewResponse.getWorkflowInstances()).hasSize(1);
    return listViewResponse.getWorkflowInstances().get(0);
  }

  private ListViewWorkflowInstanceDto getSingleWorkflowInstanceForListView() {
    return getSingleWorkflowInstanceForListView(TestUtil.createGetAllWorkflowInstancesQuery());
  }

  @Test
  public void testWorkflowInstanceAndActivityCompleted() {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .serviceTask("task1").zeebeTaskType("task1")
      .endEvent()
      .done();
    deployWorkflow(workflow, "demoProcess_v_1.bpmn");

    //when
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "task1");

    completeTask(workflowInstanceKey, "task1", null);

    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKey);

    //then
    final WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.COMPLETED);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.COMPLETED);

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(3);
    final ActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(activity.getEndDate()).isAfterOrEqualTo(testStartTime);

  }

  @Test
  public void testWorkflowInstanceStartTimeDoesNotChange() {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .serviceTask("task1").zeebeTaskType("task1")
      .endEvent()
      .done();
    deployWorkflow(workflow, "demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "task1");
    //remember start date
    final OffsetDateTime startDate = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceKey).getStartDate();

    //when
    completeTask(workflowInstanceKey, "task1", null);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKey);

    //then
    final WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.COMPLETED);
    //assert start date did not change
    assertThat(workflowInstanceEntity.getStartDate()).isEqualTo(startDate);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getStartDate()).isEqualTo(startDate);

  }

  @Test
  @Ignore("OPE-437")
  public void testSequenceFlowsPersisted() {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
      .sequenceFlowId("sf1")
      .serviceTask("task1").zeebeTaskType("task1")
      .sequenceFlowId("sf2")
      .serviceTask("task2").zeebeTaskType("task2")
      .sequenceFlowId("sf3")
      .endEvent()
      .done();
    deployWorkflow(workflow, processId + ".bpmn");

    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "task1");

    completeTask(workflowInstanceKey, "task1", null);

    completeTask(workflowInstanceKey, "task2", null);

    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKey);

    //TODO
//    WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
  
//    assertThat(workflowInstanceEntity.getSequenceFlows()).hasSize(3)
//      .extracting(IncidentTemplate.FLOW_NODE_ID).containsOnly("sf1", "sf2", "sf3");

  }

  @Test
  public void testIncidentDeleted() {
    // having
    String activityId = "taskA";

    String processId = "demoProcess";
    /*final String workflowId =*/ deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    failTaskWithNoRetriesLeft(activityId, workflowInstanceKey, "Some error");

    //when update retries
    final Long workflowInstanceId = workflowInstanceKey;
    List<IncidentEntity> allIncidents = incidentReader.getAllIncidents(workflowInstanceId);
    assertThat(allIncidents).hasSize(1);
    ZeebeTestUtil.resolveIncident(zeebeClient, allIncidents.get(0).getJobKey(), allIncidents.get(0).getKey());
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsResolvedCheck, workflowInstanceKey);

    //then
    allIncidents = incidentReader.getAllIncidents(workflowInstanceId);
    assertThat(allIncidents).hasSize(0);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.ACTIVE);

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final ActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.ACTIVE);
    assertThat(activity.getActivityId()).isEqualTo(activityId);

  }

  @Test
  public void testWorkflowInstanceWithIncidentCreated() {
    // having
    String activityId = "taskA";
    final String errorMessage = "Error occurred when working on the job";

    String processId = "demoProcess";
    final Long workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //when
    //create an incident
    failTaskWithNoRetriesLeft(activityId, workflowInstanceKey, errorMessage);

    //then
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidents(workflowInstanceKey);
    assertThat(allIncidents).hasSize(1);
    IncidentEntity incidentEntity = allIncidents.get(0);
    assertThat(incidentEntity.getWorkflowId()).isEqualTo(workflowId);
    assertThat(incidentEntity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(incidentEntity.getFlowNodeInstanceKey()).isNotNull();
    assertThat(incidentEntity.getErrorMessage()).isEqualTo(errorMessage);
    assertThat(incidentEntity.getErrorType()).isNotNull();
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.INCIDENT);

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final ActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.INCIDENT);
    assertThat(activity.getActivityId()).isEqualTo(activityId);

  }

  @Test
  public void testWorkflowInstanceWithIncidentOnGateway() {
    // having
    String activityId = "xor";

    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .exclusiveGateway(activityId)
        .sequenceFlowId("s1").condition("foo < 5")
          .serviceTask("task1").zeebeTaskType("task1")
          .endEvent()
        .moveToLastGateway()
        .sequenceFlowId("s2").condition("foo >= 5")
          .serviceTask("task2").zeebeTaskType("task2")
          .endEvent()
      .done();
    final String resourceName = processId + ".bpmn";
    final Long workflowId = deployWorkflow(workflow, resourceName);

    //when
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");      //wrong payload provokes incident
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    //then incident created, activity in INCIDENT state
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidents(workflowInstanceKey);
    assertThat(allIncidents).hasSize(1);
    IncidentEntity incidentEntity = allIncidents.get(0);
    assertThat(incidentEntity.getWorkflowId()).isEqualTo(workflowId);
    assertThat(incidentEntity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(incidentEntity.getFlowNodeInstanceKey()).isNotNull();
    assertThat(incidentEntity.getErrorMessage()).isNotEmpty();
    assertThat(incidentEntity.getErrorType()).isNotNull();
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.INCIDENT);

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final ActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.INCIDENT);
    assertThat(activity.getActivityId()).isEqualTo(activityId);

    //when payload updated
//TODO    ZeebeUtil.updateVariables(zeebeClient, gatewayActivity.getKey(), workflowInstanceId, "{\"foo\": 7}", processId, workflowId);
//    elasticsearchTestRule.processAllEvents(5);

    //then incident is resolved
//TODO    workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);
//    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
//    incidentEntity = workflowInstanceEntity.getIncidents().get(0);
//    assertThat(incidentEntity.getElementId()).isEqualTo(activityId);
//    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.RESOLVED);

    //assert activity fields
//TODO    final ActivityInstanceEntity xorActivity = workflowInstanceEntity.getActivities().stream().filter(a -> a.getElementId().equals("xor"))
//      .findFirst().get();
//    assertThat(xorActivity.getState()).isEqualTo(ActivityState.COMPLETED);
//    assertThat(xorActivity.getEndDate()).isNotNull();
  }

  @Test
  public void testWorkflowInstanceWithIncidentOnGatewayIsCanceled() {
    // having
    String activityId = "xor";

    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .exclusiveGateway(activityId)
        .sequenceFlowId("s1").condition("foo < 5")
          .serviceTask("task1").zeebeTaskType("task1")
          .endEvent()
        .moveToLastGateway()
        .sequenceFlowId("s2").condition("foo >= 5")
          .serviceTask("task2").zeebeTaskType("task2")
          .endEvent()
      .done();
    final String resourceName = processId + ".bpmn";
    final Long workflowId = deployWorkflow(workflow, resourceName);

    //when
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");      //wrong payload provokes incident
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    //then incident created, activity in INCIDENT state
    List<IncidentEntity> allIncidents = incidentReader.getAllIncidents(workflowInstanceKey);
    assertThat(allIncidents).hasSize(1);
    IncidentEntity incidentEntity = allIncidents.get(0);
    assertThat(incidentEntity.getWorkflowId()).isEqualTo(workflowId);
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    //when I cancel workflow instance
    ZeebeTestUtil.cancelWorkflowInstance(zeebeClient, workflowInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsResolvedCheck, workflowInstanceKey);

    //then incident is deleted
    WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.CANCELED);
    allIncidents = incidentReader.getAllIncidents(workflowInstanceKey);
    assertThat(allIncidents).hasSize(0);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.CANCELED);

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final ActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.TERMINATED);
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getEndDate()).isNotNull();

  }

  @Test
  public void testWorkflowInstanceGatewayIsPassed() {
    // having
    String activityId = "xor";

    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .exclusiveGateway(activityId)
        .sequenceFlowId("s1").condition("foo < 5")
          .serviceTask("task1").zeebeTaskType("task1")
          .endEvent()
        .moveToLastGateway()
        .sequenceFlowId("s2").condition("foo >= 5")
          .serviceTask("task2").zeebeTaskType("task2")
          .endEvent()
      .done();
    final String resourceName = processId + ".bpmn";
    deployWorkflow(workflow, resourceName);

    //when
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"foo\": 6}");
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "task2");

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren().size()).isGreaterThanOrEqualTo(2);
    final ActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getEndDate()).isNotNull();

  }

  @Test
  public void testWorkflowInstanceEventBasedGatewayIsActive() {
    // having
    String activityId = "gateway";

    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent()
      .eventBasedGateway(activityId)
      .intermediateCatchEvent(
        "msg-1", i -> i.message(m -> m.name("msg-1").zeebeCorrelationKey("key1")))
      .endEvent()
      .moveToLastGateway()
      .intermediateCatchEvent(
        "msg-2", i -> i.message(m -> m.name("msg-2").zeebeCorrelationKey("key2")))
      .endEvent()
      .done();
    final String resourceName = processId + ".bpmn";
    deployWorkflow(workflow, resourceName);

    //when
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"key1\": \"value1\", \"key2\": \"value2\"}");
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "gateway");

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final ActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.ACTIVE);
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getEndDate()).isNull();

  }

  @Test
  public void testWorkflowInstanceCanceled() {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();

    String processId = "demoProcess";
    /*final String workflowId =*/ deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskA");

    //when
    cancelWorkflowInstance(workflowInstanceKey);

    //then
    final WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getKey()).isEqualTo(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.CANCELED);
    assertThat(workflowInstanceEntity.getEndDate()).isNotNull();
    assertThat(workflowInstanceEntity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.CANCELED);
    assertThat(wi.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
    assertThat(wi.getEndDate()).isNotNull();
    assertThat(wi.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(wi.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final ActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.TERMINATED);
    assertThat(activity.getEndDate()).isNotNull();
    assertThat(activity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

  }

  @Test
  public void testWorkflowInstanceCanceledOnMessageEvent() {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();

    String processId = "eventProcess";
    /*final String workflowId =*/ deployWorkflow("messageEventProcess_v_1.bpmn");
//    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"clientId\": \"5\"}");

        try {
          Thread.sleep(1000L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

    //when
    cancelWorkflowInstance(workflowInstanceKey);

    //then
    final WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getKey()).isEqualTo(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.CANCELED);
    assertThat(workflowInstanceEntity.getEndDate()).isNotNull();
    assertThat(workflowInstanceEntity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.CANCELED);
    assertThat(wi.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
    assertThat(wi.getEndDate()).isNotNull();
    assertThat(wi.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(wi.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(2);
    final ActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getState()).isEqualTo(ActivityState.TERMINATED);
    assertThat(activity.getEndDate()).isNotNull();
    assertThat(activity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

  }

  @Test
  public void testWorkflowInstanceById() {
    String processId = "demoProcess";
    /*final String workflowId =*/ deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCreatedCheck, workflowInstanceKey);

    final WorkflowInstanceForListViewEntity workflowInstanceById = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceKey);
    assertThat(workflowInstanceById).isNotNull();
    assertThat(workflowInstanceById.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
  }

  @Test
  public void testWorkflowInstanceWithIncidentById() {
    String activityId = "taskA";
    final String errorMessage = "Error occurred when working on the job";
    String processId = "demoProcess";
    /*final String workflowId =*/ deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCreatedCheck, workflowInstanceKey);

    //create an incident
    failTaskWithNoRetriesLeft(activityId, workflowInstanceKey, errorMessage);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    final WorkflowInstanceForListViewEntity workflowInstanceById = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceKey);
    assertThat(workflowInstanceById).isNotNull();
    assertThat(workflowInstanceById.getState()).isEqualTo(WorkflowInstanceState.INCIDENT);
  }

  @Test(expected = NotFoundException.class)
  public void testWorkflowInstanceByIdFailForUnknownId() {
    String processId = "demoProcess";
    /*final String workflowId =*/ deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCreatedCheck, workflowInstanceKey);

    /*final WorkflowInstanceForListViewEntity workflowInstanceById =*/ workflowInstanceReader.getWorkflowInstanceById(-42L);
  }

  private void assertStartActivityCompleted(ActivityInstanceDto startActivity) {
    assertThat(startActivity.getActivityId()).isEqualTo("start");
    assertThat(startActivity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(startActivity.getType()).isEqualTo(ActivityType.START_EVENT);
    assertThat(startActivity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(startActivity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(startActivity.getEndDate()).isAfterOrEqualTo(startActivity.getStartDate());
    assertThat(startActivity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertActivityIsActive(ActivityInstanceDto activity, String activityId) {
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getState()).isEqualTo(ActivityState.ACTIVE);
    assertThat(activity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(activity.getEndDate()).isNull();
  }

}