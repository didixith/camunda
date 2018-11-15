package org.camunda.operate.es.types;

import java.io.IOException;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ImportPositionType extends StrictTypeMappingCreator {

  public static final String ALIAS_NAME = "aliasName";
  public static final String ID = "id";
  public static final String POSITION = "position";

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public String getIndexName() {
    return operateProperties.getElasticsearch().getImportPositionIndexName();
  }

  @Override
  public String getAlias() {
    return operateProperties.getElasticsearch().getImportPositionAlias();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder =  builder
      .startObject(ID)
      .field("type", "keyword")
      .endObject()
      .startObject(PARTITION_ID)
        .field("type", "integer")
      .endObject()
      .startObject(POSITION)
        .field("type", "long")
      .endObject()
      .startObject(ALIAS_NAME)
        .field("type", "keyword")
      .endObject();

    return newBuilder;
  }

}
