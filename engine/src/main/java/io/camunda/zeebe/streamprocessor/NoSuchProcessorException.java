/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.util.exception.UnrecoverableException;

public class NoSuchProcessorException extends UnrecoverableException {
  private NoSuchProcessorException(final String message) {
    super(message);
  }

  public static NoSuchProcessorException forRecord(final TypedRecord<?> record) {
    final var message =
        switch (record.getRecordType()) {
          case EVENT -> "No processor registered for event type %s"
              .formatted(record.getValueType());
          case COMMAND -> "No processor registered for command type %s"
              .formatted(record.getValueType());
          case COMMAND_REJECTION,
              SBE_UNKNOWN,
              NULL_VAL -> ("No processor registered for record type %s")
              .formatted(record.getRecordType());
        };
    return new NoSuchProcessorException(message);
  }
}
