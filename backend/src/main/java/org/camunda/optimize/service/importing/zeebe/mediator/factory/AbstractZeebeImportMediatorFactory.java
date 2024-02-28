/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.mediator.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;

@AllArgsConstructor
public abstract class AbstractZeebeImportMediatorFactory {

  protected final BeanFactory beanFactory;
  protected ImportIndexHandlerRegistry importIndexHandlerRegistry;
  protected final ConfigurationService configurationService;
  protected final ObjectMapper objectMapper;
  protected final DatabaseClient databaseClient;

  public abstract List<ImportMediator> createMediators(ZeebeDataSourceDto dataSourceDto);
}
