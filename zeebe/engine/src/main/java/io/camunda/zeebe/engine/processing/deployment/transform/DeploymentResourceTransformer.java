/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.util.Either;

interface DeploymentResourceTransformer {

  /**
   * Step 1 of the resource transformation: The transformer should add the deployed resource's
   * metadata to the deployment record, but not write an event record yet.
   *
   * @param resource the resource to transform
   * @param deployment the deployment to add the deployed resource to
   * @return either {@link Either.Right} if the resource is transformed successfully, or {@link
   *     Either.Left} if the transformation failed
   */
  Either<Failure, Void> createMetadata(
      final DeploymentResource resource, final DeploymentRecord deployment);

  // TODO comment
  /**
   * Step 2 of the resource transformation: The transformer should write an event for the resource
   * (e.g. a process record).
   *
   * @param resource the resource to transform
   * @param deployment the deployment containing the metadata created in {@link
   *     DeploymentResourceTransformer#createMetadata(DeploymentResource, DeploymentRecord)}
   * @return either {@link Either.Right} if the resource is transformed successfully, or {@link
   *     Either.Left} if the transformation failed
   */
  Either<Failure, Void> writeRecords(DeploymentResource resource, DeploymentRecord deployment);
}
