/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index;

import static org.camunda.optimize.service.db.DatabaseConstants.MAPPING_ENABLED_SETTING;

import java.io.IOException;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.elasticsearch.xcontent.XContentBuilder;

public abstract class DecisionDefinitionIndex<TBuilder> extends AbstractDefinitionIndex<TBuilder> {

  public static final int VERSION = 5;

  public static final String DECISION_DEFINITION_ID = DEFINITION_ID;
  public static final String DECISION_DEFINITION_KEY = DEFINITION_KEY;
  public static final String DECISION_DEFINITION_VERSION = DEFINITION_VERSION;
  public static final String DECISION_DEFINITION_VERSION_TAG = DEFINITION_VERSION_TAG;
  public static final String DECISION_DEFINITION_NAME = DEFINITION_NAME;
  public static final String DECISION_DEFINITION_XML = "dmn10Xml";
  public static final String TENANT_ID = DEFINITION_TENANT_ID;
  public static final String INPUT_VARIABLE_NAMES = "inputVariableNames";
  public static final String OUTPUT_VARIABLE_NAMES = "outputVariableNames";

  @Override
  public String getIndexName() {
    return DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return super.addProperties(xContentBuilder)
        .startObject(INPUT_VARIABLE_NAMES)
        .field(MAPPING_ENABLED_SETTING, "false")
        .endObject()
        .startObject(OUTPUT_VARIABLE_NAMES)
        .field(MAPPING_ENABLED_SETTING, "false")
        .endObject()
        .startObject(DECISION_DEFINITION_XML)
        .field("type", "text")
        .field("index", true)
        .field("analyzer", "is_present_analyzer")
        .endObject();
    // @formatter:on
  }
}
