/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

@Data
public class UpgradeExecutionDependencies {

  protected final ConfigurationService configurationService;
  protected final OptimizeIndexNameService indexNameService;
  protected final OptimizeElasticsearchClient esClient;
  protected final ObjectMapper objectMapper;
  protected final ElasticSearchMetadataService metadataService;
}
