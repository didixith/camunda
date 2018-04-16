package org.camunda.optimize.service.es.schema.type.index;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DefinitionImportIndexType extends StrictTypeMappingCreator {

  public static final String PROCESS_DEFINITIONS_TO_IMPORT = "processDefinitionsToImport";
  public static final String CURRENT_PROCESS_DEFINITION = "currentProcessDefinition";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String TIMESTAMP_OF_LAST_ENTITY = "timestampOfLastEntity";
  public static final String ES_TYPE_INDEX_REFERS_TO = "esTypeIndexRefersTo";
  private static final String ENGINE = "engine";

  @Override
  public String getType() {
    return configurationService.getProcessDefinitionImportIndexType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    XContentBuilder newBuilder = xContentBuilder
      .startObject(ENGINE)
        .field("type", "keyword")
      .endObject()
      .startObject(ES_TYPE_INDEX_REFERS_TO)
        .field("type", "keyword")
      .endObject()
      .startObject(CURRENT_PROCESS_DEFINITION)
        .field("type", "nested")
        .startObject("properties");
          addNestedDefinitionInformation(newBuilder)
        .endObject()
      .endObject()
      .startObject(PROCESS_DEFINITIONS_TO_IMPORT)
        .field("type", "nested")
        .startObject("properties");
          addNestedDefinitionInformation(newBuilder)
        .endObject()
      .endObject();
    return newBuilder;
  }

  private XContentBuilder addNestedDefinitionInformation(XContentBuilder builder) throws IOException {
    return builder
      .startObject(PROCESS_DEFINITION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(TIMESTAMP_OF_LAST_ENTITY)
        .field("type", "date")
        .field("format",configurationService.getOptimizeDateFormat())
      .endObject();
  }
}
