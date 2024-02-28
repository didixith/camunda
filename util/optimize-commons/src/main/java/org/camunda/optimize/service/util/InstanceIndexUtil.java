/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.DatabaseConstants.INDEX_NOT_FOUND_EXCEPTION_TYPE;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;

import java.util.Arrays;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.ElasticsearchStatusException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InstanceIndexUtil {

  public static String[] getDecisionInstanceIndexAliasName(
      final DecisionReportDataDto reportDataDto) {
    // for decision reports only one (the first) definition is supported
    return reportDataDto.getDefinitions().stream()
        .findFirst()
        .map(ReportDataDefinitionDto::getKey)
        .map(InstanceIndexUtil::getDecisionInstanceIndexAliasName)
        .map(value -> new String[] {value})
        .orElse(new String[] {DECISION_INSTANCE_MULTI_ALIAS});
  }

  public static String getDecisionInstanceIndexAliasName(final String decisionDefinitionKey) {
    if (decisionDefinitionKey == null) {
      return DECISION_INSTANCE_MULTI_ALIAS;
    } else {
      return DecisionInstanceIndex.constructIndexName(decisionDefinitionKey);
    }
  }

  public static String[] getProcessInstanceIndexAliasNames(
      final ProcessReportDataDto reportDataDto) {
    if (reportDataDto.isManagementReport()) {
      return new String[] {PROCESS_INSTANCE_MULTI_ALIAS};
    }
    return !reportDataDto.getDefinitions().isEmpty()
        ? reportDataDto.getDefinitions().stream()
            .map(ReportDataDefinitionDto::getKey)
            .map(InstanceIndexUtil::getProcessInstanceIndexAliasName)
            .toArray(String[]::new)
        : new String[] {PROCESS_INSTANCE_MULTI_ALIAS};
  }

  public static String getProcessInstanceIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return PROCESS_INSTANCE_MULTI_ALIAS;
    } else {
      return ProcessInstanceIndex.constructIndexName(processDefinitionKey);
    }
  }

  public static boolean isInstanceIndexNotFoundException(final ElasticsearchStatusException e) {
    return Arrays.stream(e.getSuppressed())
        .map(Throwable::getMessage)
        .anyMatch(
            msg ->
                msg.contains(INDEX_NOT_FOUND_EXCEPTION_TYPE)
                    && (containsInstanceIndexAliasOrPrefix(PROCESS, msg)
                        || containsInstanceIndexAliasOrPrefix(DECISION, msg)));
  }

  public static boolean isInstanceIndexNotFoundException(
      final DefinitionType type, final ElasticsearchStatusException e) {
    return Arrays.stream(e.getSuppressed())
        .map(Throwable::getMessage)
        .anyMatch(
            msg ->
                msg.contains(INDEX_NOT_FOUND_EXCEPTION_TYPE)
                    && containsInstanceIndexAliasOrPrefix(type, msg));
  }

  private static boolean containsInstanceIndexAliasOrPrefix(
      final DefinitionType type, final String message) {
    switch (type) {
      case PROCESS:
        return message.contains(PROCESS_INSTANCE_INDEX_PREFIX)
            || message.contains(PROCESS_INSTANCE_MULTI_ALIAS);
      case DECISION:
        return message.contains(DECISION_INSTANCE_INDEX_PREFIX)
            || message.contains(DECISION_INSTANCE_MULTI_ALIAS);
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }
}
