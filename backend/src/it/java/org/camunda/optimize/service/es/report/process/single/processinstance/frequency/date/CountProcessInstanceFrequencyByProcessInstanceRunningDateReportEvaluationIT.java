/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.date;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.hamcrest.CoreMatchers.is;

public class CountProcessInstanceFrequencyByProcessInstanceRunningDateReportEvaluationIT
  extends AbstractCountProcessInstanceFrequencyByProcessInstanceDateReportEvaluationIT {

  @Override
  protected ProcessReportDataType getTestReportDataType() {
    return ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_RUNNING_DATE;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.RUNNING_DATE;
  }

  @Override
  protected void changeProcessInstanceDate(final String processInstanceId,
                                           final OffsetDateTime newDate) {
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceId, newDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceId, newDate);
  }

  @Override
  protected void updateProcessInstanceDates(final Map<String, OffsetDateTime> newIdToDates) {
    engineDatabaseExtension.changeProcessInstanceStartDates(newIdToDates);
    engineDatabaseExtension.changeProcessInstanceEndDates(newIdToDates);
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticGroupByDateUnits")
  public void countRunningInstances_instancesFallIntoMultipleBuckets(final GroupByDateUnit unit) {
    // given
    // first instance starts within a bucket (as opposed to on the "edge" of a bucket)
    final OffsetDateTime startOfFirstInstance = OffsetDateTime.parse("2020-06-15T12:00:00+02:00").withSecond(10);
    final Duration bucketWidth = mapToChronoUnit(unit).getDuration();
    final List<ProcessInstanceEngineDto> processInstanceDtos =
      startAndEndProcessInstancesWithGivenRuntime(2, bucketWidth, startOfFirstInstance);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessInstanceEngineDto instance = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = getGroupByRunningDateReportData(
      instance.getProcessDefinitionKey(),
      instance.getProcessDefinitionVersion(),
      unit
    );
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final int expectedNumberOfBuckets = 3;
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData)
      .isNotNull()
      .hasSize(expectedNumberOfBuckets);

    // bucket keys exist for each unit between start date of first and end date of last instance
    final ZonedDateTime lastBucketStartDate = truncateToStartOfUnit(
      startOfFirstInstance.plus(bucketWidth.multipliedBy(expectedNumberOfBuckets - 1)),
      mapToChronoUnit(unit)
    );
    IntStream.range(0, expectedNumberOfBuckets)
      .forEach(i -> {
        final String expectedBucketKey = convertToExpectedBucketKey(
          startOfFirstInstance.plus(i, mapToChronoUnit(unit)),
          unit
        );
        assertThat(resultData.get(i).getKey()).isEqualTo(expectedBucketKey);
      });

    // instances fall into correct buckets (overlapping in the 2nd bucket)
    assertThat(resultData.get(0).getValue()).isEqualTo(1.);
    assertThat(resultData.get(1).getValue()).isEqualTo(2.);
    assertThat(resultData.get(2).getValue()).isEqualTo(1.);
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticGroupByDateUnits")
  public void countRunningInstances_runningInstancesOnly(final GroupByDateUnit unit) {
    // given two running instances
    final Duration bucketWidth = mapToChronoUnit(unit).getDuration();
    final OffsetDateTime startOfFirstInstance = OffsetDateTime.parse("2020-01-05T12:00:00+02:00");
    final OffsetDateTime startOfSecondInstance = OffsetDateTime.parse("2020-01-05T12:00:00+02:00").plus(bucketWidth);

    ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();
    ProcessInstanceEngineDto instance1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto instance2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(instance1.getId(), startOfFirstInstance);
    engineDatabaseExtension.changeProcessInstanceStartDate(instance2.getId(), startOfSecondInstance);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getGroupByRunningDateReportData(
      processDefinition.getKey(),
      processDefinition.getVersionAsString(),
      unit
    );
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then the bucket range is based on earliest and latest start date
    final List<MapResultEntryDto> resultData = result.getData();

    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(2);
    assertThat(resultData.get(0).getKey()).isEqualTo(convertToExpectedBucketKey(startOfFirstInstance, unit));
    assertThat(resultData.get(1).getKey()).isEqualTo(convertToExpectedBucketKey(startOfSecondInstance, unit));
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticGroupByDateUnits")
  public void countRunningInstances_latestStartDateAfterLatestEndDate(final GroupByDateUnit unit) {
    // given instances whose latest start date is after the latest end date
    final Duration bucketWidth = mapToChronoUnit(unit).getDuration();
    final OffsetDateTime earliestStartDate = OffsetDateTime.parse("2020-01-05T12:00:10+02:00");
    final OffsetDateTime latestStartDate = OffsetDateTime.parse("2020-01-05T12:00:10+02:00")
      .plus(bucketWidth.multipliedBy(3));
    ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();
    ProcessInstanceEngineDto instance1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto instance2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto instance3 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(instance1.getId(), earliestStartDate);
    engineDatabaseExtension.changeProcessInstanceStartAndEndDate(
      instance2.getId(),
      earliestStartDate,
      earliestStartDate.plus(bucketWidth)
    );
    engineDatabaseExtension.changeProcessInstanceStartDate(instance3.getId(), latestStartDate);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getGroupByRunningDateReportData(
      processDefinition.getKey(),
      processDefinition.getVersionAsString(),
      unit
    );
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then the bucket range is earliest to latest start date
    final List<MapResultEntryDto> resultData = result.getData();

    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(4);
    assertThat(resultData.get(0).getKey()).isEqualTo(convertToExpectedBucketKey(earliestStartDate, unit));
    assertThat(resultData.get(3).getKey()).isEqualTo(convertToExpectedBucketKey(latestStartDate, unit));
  }

  @Override
  protected void assertStartDateResultMap(List<MapResultEntryDto> resultData,
                                          int size,
                                          OffsetDateTime now,
                                          ChronoUnit unit,
                                          Double expectedValue) {
    MatcherAssert.assertThat(resultData.size(), is(size));
    final ZonedDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit);
    IntStream.range(0, size)
      .forEach(i -> {
        final String expectedDateString = localDateTimeToString(finalStartOfUnit.minus((i), unit));
        MatcherAssert.assertThat(resultData.get(i).getKey(), is(expectedDateString));
        MatcherAssert.assertThat(resultData.get(i).getValue(), is(expectedValue));
      });
  }

  private ProcessReportDataDto getGroupByRunningDateReportData(final String key,
                                                               final String version,
                                                               final GroupByDateUnit unit) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(key)
      .setProcessDefinitionVersion(version)
      .setDateInterval(unit)
      .setReportDataType(getTestReportDataType())
      .build();
  }

  private String convertToExpectedBucketKey(final OffsetDateTime date, final GroupByDateUnit unit) {
    return localDateTimeToString(
      truncateToStartOfUnit(date, mapToChronoUnit(unit))
    );
  }

  private ProcessDefinitionEngineDto deployUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
  }
}
