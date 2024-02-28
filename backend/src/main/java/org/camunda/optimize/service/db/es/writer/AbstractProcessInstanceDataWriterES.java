/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import org.camunda.optimize.service.db.writer.AbstractProcessInstanceDataWriter;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;

@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class AbstractProcessInstanceDataWriterES<T extends OptimizeDto>
    implements AbstractProcessInstanceDataWriter<T> {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final OptimizeElasticsearchClient esClient;
  protected final ElasticSearchSchemaManager elasticSearchSchemaManager;

  private final Set<String> existingInstanceIndexDefinitionKeys = ConcurrentHashMap.newKeySet();

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    existingInstanceIndexDefinitionKeys.clear();
  }

  @Override
  public void createInstanceIndicesIfMissing(
      final List<T> optimizeDtos, final Function<T, String> definitionKeyGetter) {
    createInstanceIndicesIfMissing(optimizeDtos.stream().map(definitionKeyGetter).collect(toSet()));
  }

  @Override
  public void createInstanceIndicesIfMissing(final Set<String> processDefinitionKeys) {
    final Set<String> missingInstanceIndexKeys = new HashSet<>(processDefinitionKeys);
    missingInstanceIndexKeys.removeIf(this::indexExists);
    if (!missingInstanceIndexKeys.isEmpty()) {
      createMissingInstanceIndices(missingInstanceIndexKeys);
    }
  }

  private void createMissingInstanceIndices(final Set<String> defKeysOfMissingIndices) {
    log.debug(
        "Creating process instance indices for definition keys [{}].", defKeysOfMissingIndices);
    defKeysOfMissingIndices.forEach(
        defKey ->
            elasticSearchSchemaManager.createOrUpdateOptimizeIndex(
                esClient,
                new ProcessInstanceIndexES(defKey),
                Collections.singleton(PROCESS_INSTANCE_MULTI_ALIAS)));
    existingInstanceIndexDefinitionKeys.addAll(defKeysOfMissingIndices);
  }

  private boolean indexExists(final String definitionKey) {
    return existingInstanceIndexDefinitionKeys.contains(definitionKey)
        || elasticSearchSchemaManager.indexExists(
            esClient, getProcessInstanceIndexAliasName(definitionKey));
  }
}
