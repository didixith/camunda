package org.camunda.optimize.service.es;

import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.type.AlertType;
import org.camunda.optimize.service.es.schema.type.DashboardShareType;
import org.camunda.optimize.service.es.schema.type.DashboardType;
import org.camunda.optimize.service.es.schema.type.DurationHeatmapTargetValueType;
import org.camunda.optimize.service.es.schema.type.EventType;
import org.camunda.optimize.service.es.schema.type.FinishedProcessInstanceIdTrackingType;
import org.camunda.optimize.service.es.schema.type.LicenseType;
import org.camunda.optimize.service.es.schema.type.MetadataType;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlTrackingType;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.es.schema.type.ReportShareType;
import org.camunda.optimize.service.es.schema.type.ReportType;
import org.camunda.optimize.service.es.schema.type.RunningProcessInstanceIdTrackingType;
import org.camunda.optimize.service.es.schema.type.VariableType;
import org.camunda.optimize.service.es.schema.type.index.ImportIndexType;
import org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class ElasticSearchSchemaInitializer {
  private boolean initialized = false;
  private final Logger logger = LoggerFactory.getLogger(ElasticSearchSchemaInitializer.class);
  @Autowired
  private ElasticSearchSchemaManager schemaManager;

  @Autowired
  private EventType eventType;

  @Autowired
  private VariableType variableType;

  @Autowired
  private DurationHeatmapTargetValueType targetValueType;

  @Autowired
  private ProcessDefinitionType processDefinitionType;

  @Autowired
  private ProcessDefinitionXmlType processDefinitionXmlType;

  @Autowired
  private LicenseType licenseType;

  @Autowired
  private ProcessInstanceType processInstanceType;

  @Autowired
  private ImportIndexType importIndexType;

  @Autowired
  private FinishedProcessInstanceIdTrackingType finishedProcessInstanceIdTrackingType;

  @Autowired
  private RunningProcessInstanceIdTrackingType runningProcessInstanceIdTrackingType;

  @Autowired
  private ProcessDefinitionXmlTrackingType processDefinitionXmlTrackingType;

  @Autowired
  private TimestampBasedImportIndexType timestampBasedImportIndexType;

  @Autowired
  private ReportType reportType;

  @Autowired
  private DashboardType dashboardType;

  @Autowired
  private AlertType alertType;

  @Autowired
  private ReportShareType reportShareType;

  @Autowired
  private DashboardShareType dashboardShareType;

  @Autowired
  private MetadataType metadataType;

  public void initializeSchema() {
    if (!initialized) {
      try {
        if (!schemaManager.schemaAlreadyExists()) {
          schemaManager.createOptimizeIndex();
        }
        schemaManager.updateMappings();
        initialized = true;
      } catch (NoNodeAvailableException e) {
        logger.error("can't handle schema initialization\\update", e);
      }

    }
  }

  @PostConstruct
  public void initializeMappings() {
    schemaManager.addMapping(eventType);
    schemaManager.addMapping(finishedProcessInstanceIdTrackingType);
    schemaManager.addMapping(runningProcessInstanceIdTrackingType);
    schemaManager.addMapping(processDefinitionXmlTrackingType);
    schemaManager.addMapping(variableType);
    schemaManager.addMapping(processDefinitionType);
    schemaManager.addMapping(processDefinitionXmlType);
    schemaManager.addMapping(importIndexType);
    schemaManager.addMapping(targetValueType);
    schemaManager.addMapping(processInstanceType);
    schemaManager.addMapping(timestampBasedImportIndexType);
    schemaManager.addMapping(licenseType);
    schemaManager.addMapping(reportType);
    schemaManager.addMapping(dashboardType);
    schemaManager.addMapping(alertType);
    schemaManager.addMapping(reportShareType);
    schemaManager.addMapping(dashboardShareType);
    schemaManager.addMapping(metadataType);
  }

  /**
   * This method has to be invoked before schema initialization can be triggered
   */
  public void useClient(Client instance, ConfigurationService configurationService) {
    schemaManager.setEsclient(instance);
    schemaManager.setConfigurationService(configurationService);
  }

  public boolean isInitialized() {
    return initialized;
  }

  public void setInitialized(boolean initialized) {
    this.initialized = initialized;
  }
}
