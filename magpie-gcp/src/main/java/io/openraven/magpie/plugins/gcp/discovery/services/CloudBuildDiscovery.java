/*
 * Copyright 2021 Open Raven Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.openraven.magpie.plugins.gcp.discovery.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.devtools.cloudbuild.v1.CloudBuildClient;
import io.openraven.magpie.api.Emitter;
import io.openraven.magpie.api.MagpieGcpResource;
import io.openraven.magpie.api.Session;
import io.openraven.magpie.data.gcp.cloudbuild.CloudBuild;
import io.openraven.magpie.data.gcp.cloudbuild.CloudBuildTrigger;
import io.openraven.magpie.plugins.gcp.discovery.exception.DiscoveryExceptions;
import io.openraven.magpie.plugins.gcp.discovery.GCPUtils;
import io.openraven.magpie.plugins.gcp.discovery.VersionedMagpieEnvelopeProvider;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

public class CloudBuildDiscovery implements GCPDiscovery {
  private static final String SERVICE = "cloudBuild";

  @Override
  public String service() {
    return SERVICE;
  }

  public void discover(ObjectMapper mapper, String projectId, Session session, Emitter emitter, Logger logger) {
    try (CloudBuildClient cloudBuildClient = CloudBuildClient.create()) {
      discoverBuildTriggers(mapper, projectId, session, emitter, cloudBuildClient);
      discoverBuilds(mapper, projectId, session, emitter, cloudBuildClient);
    } catch (IOException e) {
      DiscoveryExceptions.onDiscoveryException("CloudBuild", e);
    }
  }

  private void discoverBuildTriggers(ObjectMapper mapper, String projectId, Session session, Emitter emitter, CloudBuildClient cloudBuildClient) {
    final String RESOURCE_TYPE = CloudBuildTrigger.RESOURCE_TYPE;

    for (var element : cloudBuildClient.listBuildTriggers(projectId).iterateAll()) {
      var data = new MagpieGcpResource.MagpieGcpResourceBuilder(mapper, element.getName())
        .withProjectId(projectId)
        .withResourceType(RESOURCE_TYPE)
        .withConfiguration(GCPUtils.asJsonNode(element))
        .build();

      emitter.emit(VersionedMagpieEnvelopeProvider.create(session, List.of(fullService() + ":buildTrigger"), data.toJsonNode()));
    }
  }

  private void discoverBuilds(ObjectMapper mapper, String projectId, Session session, Emitter emitter, CloudBuildClient cloudBuildClient) {
    final String RESOURCE_TYPE = CloudBuild.RESOURCE_TYPE;

    for (var element : cloudBuildClient.listBuilds(projectId, "").iterateAll()) {
      var data = new MagpieGcpResource.MagpieGcpResourceBuilder(mapper, element.getName())
        .withProjectId(projectId)
        .withResourceType(RESOURCE_TYPE)
        .withConfiguration(GCPUtils.asJsonNode(element))
        .build();

      emitter.emit(VersionedMagpieEnvelopeProvider.create(session, List.of(fullService() + ":builds"), data.toJsonNode()));
    }
  }
}
