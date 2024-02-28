/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.sharing;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ShareSearchRequestDto {
  private List<String> reports = new ArrayList<>();
  private List<String> dashboards = new ArrayList<>();
}
