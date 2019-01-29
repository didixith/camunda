package org.camunda.optimize.upgrade.steps.document;

import com.google.common.collect.ImmutableMap;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;

public class UpgradeCombinedReportSettingsFrom23Step extends AbstractReportConfigurationUpdateStep {
  public UpgradeCombinedReportSettingsFrom23Step(final Map defaultReportConfiguration,
                                                 final Map<String, Map> singleReportsViewAndVisualization) {
    super(
      COMBINED_REPORT_TYPE,
      // TODO: match all query for now, need to add 2.3.0 specific query when implementing OPT-1843
      QueryBuilders.matchAllQuery(),
      getDeepCopyMapScript() +
        "def reportData = ctx._source.data;\n" +
        "def singleReports = params.singleReports;\n" +
        "def newConfig = deepCopyMap(params.defaultConfiguration);\n" +
        getMigrateCompatibleFieldsScript() +
        // incompatible field migration
        "if (reportData.configuration?.targetValue != null\n" +
        "    && reportData.reportIds != null\n" +
        "    && singleReports != null\n" +
        "    && reportData.reportIds.length > 0) {\n" +
        "  def firstReportId = reportData.reportIds[0];\n" +
        "  def singleReportData = singleReports.get(firstReportId);\n" +
        "  if (singleReportData != null) {\n" +
        //   #1 IF visualization is line OR bar OR number
        "    if (singleReportData.visualization == \"line\" " +
        "        || singleReportData.visualization == \"bar\"\n" +
        "        || singleReportData.visualization == \"number\") {\n" +
        //     #1.1 AND view property is frequency
        "      if (singleReportData.view?.property == \"frequency\") {\n" +
        //       store target as value and isBelow in countChart
        "        newConfig.targetValue.active = !!reportData.configuration.targetValue.active;\n" +
        "        newConfig.targetValue.countChart.value = reportData.configuration.targetValue.values?.target;\n" +
        "        newConfig.targetValue.countChart.isBelow = reportData.configuration.targetValue.values?.isBelow;\n" +
        "      }\n" +
        //     #1.2 AND view property is duration
        "      else if (singleReportData.view?.property == \"duration\") {\n" +
        //       store target as value, dateFormat as unit and isBelow as durationChart
        "        newConfig.targetValue.active = reportData.configuration.targetValue.active;\n" +
        "        newConfig.targetValue.durationChart.value = reportData.configuration.targetValue.values.target;\n" +
        "        if (reportData.configuration.targetValue.values.dateFormat != null\n" +
        "            && reportData.configuration.targetValue.values.dateFormat != \"\") {\n" +
        "          newConfig.targetValue.durationChart.unit = reportData.configuration.targetValue.values.dateFormat;\n" +
        "        }\n" +
        "        newConfig.targetValue.durationChart.isBelow = reportData.configuration.targetValue.values.isBelow;\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +
        "reportData.configuration = newConfig;\n",
      ImmutableMap.of(
        "defaultConfiguration", defaultReportConfiguration,
        "singleReports", singleReportsViewAndVisualization
      )
    );
  }

}
