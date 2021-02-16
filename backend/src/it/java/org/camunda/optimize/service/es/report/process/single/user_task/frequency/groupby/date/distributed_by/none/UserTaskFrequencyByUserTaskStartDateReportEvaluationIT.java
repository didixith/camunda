/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.frequency.groupby.date.distributed_by.none;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;

public class UserTaskFrequencyByUserTaskStartDateReportEvaluationIT
  extends UserTaskFrequencyByUserTaskDateReportEvaluationIT {

  @ParameterizedTest
  @MethodSource("getFlowNodeStatusExpectedValues")
  public void evaluateReportWithFlowNodeStatusFilter(FlowNodeStatusTestValues flowNodeStatusTestValues) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoModelElementDefinition();
    final ProcessInstanceEngineDto processInstanceDto = startAndCompleteInstance(processDefinition.getId());

    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.DAY);
    reportData.setFilter(flowNodeStatusTestValues.processFilter);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(flowNodeStatusTestValues.expectedInstanceCount);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(result.getEntryForKey(localDateTimeToString(startOfToday)))
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(flowNodeStatusTestValues.resultValue);
  }

  @Test
  public void evaluateReportWithFlowNodeStatusFilterCanceled() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoModelElementDefinition();
    startAndCompleteInstance(processDefinition.getId());

    final ProcessInstanceEngineDto secondInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(secondInstance.getId(), USER_TASK_1);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.DAY);
    reportData.setFilter(ProcessFilterBuilder.filter().canceledFlowNodesOnly().add().buildList());
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(result.getEntryForKey(localDateTimeToString(startOfToday)))
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.);
  }

  @Data
  @AllArgsConstructor
  static class FlowNodeStatusTestValues {
    List<ProcessFilterDto<?>> processFilter;
    Double resultValue;
    Long expectedInstanceCount;
  }

  protected static Stream<FlowNodeStatusTestValues> getFlowNodeStatusExpectedValues() {
    return Stream.of(
      new FlowNodeStatusTestValues(
        ProcessFilterBuilder.filter().runningFlowNodesOnly().add().buildList(), 1., 1L),
      new FlowNodeStatusTestValues(
        ProcessFilterBuilder.filter().completedFlowNodesOnly().add().buildList(), 2., 2L),
      new FlowNodeStatusTestValues(
        ProcessFilterBuilder.filter().completedOrCanceledFlowNodesOnly().add().buildList(), 2., 2L)
    );
  }

  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE;
  }

  @Override
  protected void changeModelElementDates(final Map<String, OffsetDateTime> updates) {
    engineDatabaseExtension.changeUserTaskStartDates(updates);
  }

  @Override
  protected void changeModelElementDate(final ProcessInstanceEngineDto processInstance, final String modelElementId,
                                        final OffsetDateTime dateToChangeTo) {
    engineDatabaseExtension.changeUserTaskStartDate(processInstance.getId(), modelElementId, dateToChangeTo);
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

}
