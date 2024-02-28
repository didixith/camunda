/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.SETTINGS_INDEX_NAME;
import static org.camunda.optimize.service.db.schema.index.SettingsIndex.LAST_MODIFIED;
import static org.camunda.optimize.service.db.schema.index.SettingsIndex.LAST_MODIFIER;
import static org.camunda.optimize.service.db.schema.index.SettingsIndex.METADATA_TELEMETRY_ENABLED;
import static org.camunda.optimize.service.db.schema.index.SettingsIndex.SHARING_ENABLED;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.BadRequestException;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.schema.index.SettingsIndex;
import org.camunda.optimize.service.db.writer.SettingsWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Slf4j
@Component
@Conditional(OpenSearchCondition.class)
public class SettingWriterOS implements SettingsWriter {

  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;

  @Override
  public void upsertSettings(final SettingsResponseDto settingsDto) {
    log.debug("Writing settings to OpenSearch");
    final UpdateRequest.Builder<SettingsResponseDto, Void> request =
        createSettingsUpsert(settingsDto);

    final String errorMessage = "There were errors while writing settings to OpenSearch.";
    osClient.upsert(request, SettingsResponseDto.class, e -> errorMessage);
  }

  private UpdateRequest.Builder<SettingsResponseDto, Void> createSettingsUpsert(
      final SettingsResponseDto settingsDto) {
    Set<String> fieldsToUpdate = new HashSet<>();

    if (settingsDto.getMetadataTelemetryEnabled().isPresent()) {
      fieldsToUpdate.addAll(Set.of(LAST_MODIFIER, METADATA_TELEMETRY_ENABLED));
    }
    if (settingsDto.getSharingEnabled().isPresent()) {
      fieldsToUpdate.add(SHARING_ENABLED);
    }
    if (!fieldsToUpdate.isEmpty()) {
      // This always gets updated
      fieldsToUpdate.add(LAST_MODIFIED);
    } else {
      throw new BadRequestException("No settings can be updated, as no values are present!");
    }

    final Script updateScript =
        OpenSearchWriterUtil.createFieldUpdateScript(fieldsToUpdate, settingsDto, objectMapper);

    return new UpdateRequest.Builder<SettingsResponseDto, Void>()
        .index(SETTINGS_INDEX_NAME)
        .id(SettingsIndex.ID)
        .upsert(settingsDto)
        .script(updateScript)
        .refresh(Refresh.True)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
  }
}
