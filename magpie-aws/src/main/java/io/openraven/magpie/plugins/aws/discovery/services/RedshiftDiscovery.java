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

package io.openraven.magpie.plugins.aws.discovery.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openraven.magpie.api.Emitter;
import io.openraven.magpie.api.MagpieResource;
import io.openraven.magpie.api.Session;
import io.openraven.magpie.plugins.aws.discovery.AWSUtils;
import io.openraven.magpie.plugins.aws.discovery.DiscoveryExceptions;
import io.openraven.magpie.plugins.aws.discovery.VersionedMagpieEnvelopeProvider;
import org.javatuples.Pair;
import org.slf4j.Logger;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.redshift.RedshiftClient;
import software.amazon.awssdk.services.redshift.model.Cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.openraven.magpie.plugins.aws.discovery.AWSUtils.getAwsResponse;
import static io.openraven.magpie.plugins.aws.discovery.AWSUtils.getCloudwatchDoubleMetricMaximum;

public class RedshiftDiscovery implements AWSDiscovery {

  private static final String SERVICE = "redshift";

  @Override
  public String service() {
    return SERVICE;
  }

  @Override
  public List<Region> getSupportedRegions() {
    return RedshiftClient.serviceMetadata().regions();
  }

  @Override
  public void discover(ObjectMapper mapper, Session session, Region region, Emitter emitter, Logger logger, String account) {
    final var client = AWSUtils.configure(RedshiftClient.builder(), region);
    final String RESOURCE_TYPE = "AWS::Redshift::Cluster";

    try {
      client.describeClustersPaginator().clusters().stream().forEach(cluster -> {
        String arn = String.format("arn:aws:redshift:%s:%s:cluster:%s", region, account, cluster.clusterIdentifier());
        var data = new MagpieResource.MagpieResourceBuilder(mapper, arn)
          .withResourceName(cluster.dbName())
          .withResourceId(cluster.clusterIdentifier())
          .withResourceType(RESOURCE_TYPE)
          .withConfiguration(mapper.valueToTree(cluster.toBuilder()))
          .withCreatedIso(cluster.clusterCreateTime())
          .withAccountId(account)
          .withRegion(region.toString())
          .build();

        discoverStorage(client, data);
        discoverSize(cluster, data, region);

        emitter.emit(VersionedMagpieEnvelopeProvider.create(session, List.of(fullService() + ":cluster"), data.toJsonNode()));
      });
    } catch (SdkServiceException | SdkClientException ex) {
      DiscoveryExceptions.onDiscoveryException(RESOURCE_TYPE, null, region, ex);
    }
  }

  private void discoverStorage(RedshiftClient client, MagpieResource data) {
    final String keyname = "storage";

    getAwsResponse(
      client::describeStorage,
      (resp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, resp)),
      (noresp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, noresp))
    );
  }

  private void discoverSize(Cluster resource, MagpieResource data, Region region) {
    try {
      List<Dimension> dimensions = new ArrayList<>();
      dimensions.add(Dimension.builder().name("ClusterIdentifier").value(resource.clusterIdentifier()).build());
      Pair<Double, GetMetricStatisticsResponse> percentageDiskSpaceUsed =
        getCloudwatchDoubleMetricMaximum(region.toString(), "AWS/Redshift", "PercentageDiskSpaceUsed", dimensions);

      AWSUtils.update(data.supplementaryConfiguration, Map.of("PercentageDiskSpaceUsed", percentageDiskSpaceUsed.getValue0()));

      // pull the relevant node(s) from the payload object. See https://boto3.amazonaws.com/v1/documentation/api/latest/reference/services/redshift.html
      JsonNode storageCapacityNode = data.supplementaryConfiguration.at("storage/totalProvisionedStorageInMegaBytes");
      JsonNode usedPctNode = data.supplementaryConfiguration.at("storage/PercentageDiskSpaceUsed");
      if (!storageCapacityNode.isMissingNode() && !usedPctNode.isMissingNode()) {
        long capacityAsBytes = storageCapacityNode.asLong() * 1049000L;
        @SuppressWarnings("WrapperTypeMayBePrimitive")
        Double dataUsed = (usedPctNode.asDouble() / 100) * capacityAsBytes;

        data.sizeInBytes = dataUsed.longValue();
        data.maxSizeInBytes = capacityAsBytes;
      }
    } catch (Exception ignored) {
    }
  }
}