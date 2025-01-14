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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openraven.magpie.api.Emitter;
import io.openraven.magpie.api.MagpieAwsResource;
import io.openraven.magpie.api.Session;
import io.openraven.magpie.data.aws.glacier.GlacierVault;
import io.openraven.magpie.plugins.aws.discovery.AWSUtils;
import io.openraven.magpie.plugins.aws.discovery.DiscoveryExceptions;
import io.openraven.magpie.plugins.aws.discovery.MagpieAWSClientCreator;
import io.openraven.magpie.plugins.aws.discovery.VersionedMagpieEnvelopeProvider;
import org.slf4j.Logger;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.glacier.GlacierClient;
import software.amazon.awssdk.services.glacier.model.DescribeVaultOutput;
import software.amazon.awssdk.services.glacier.model.GetVaultAccessPolicyRequest;
import software.amazon.awssdk.services.glacier.model.GetVaultLockRequest;
import software.amazon.awssdk.services.glacier.model.GetVaultNotificationsRequest;
import software.amazon.awssdk.services.glacier.model.GlacierJobDescription;
import software.amazon.awssdk.services.glacier.model.ListJobsRequest;
import software.amazon.awssdk.services.glacier.model.ListMultipartUploadsRequest;
import software.amazon.awssdk.services.glacier.model.ListTagsForVaultRequest;
import software.amazon.awssdk.services.glacier.model.UploadListElement;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.openraven.magpie.plugins.aws.discovery.AWSUtils.getAwsResponse;

public class GlacierDiscovery implements AWSDiscovery {

  private static final String SERVICE = "glacier";

  @Override
  public String service() {
    return SERVICE;
  }

  @Override
  public List<Region> getSupportedRegions() {
    return GlacierClient.serviceMetadata().regions();
  }

  @Override
  public void discover(ObjectMapper mapper, Session session, Region region, Emitter emitter, Logger logger, String account, MagpieAWSClientCreator clientCreator) {
    final String RESOURCE_TYPE = GlacierVault.RESOURCE_TYPE;

    try (final var client = clientCreator.apply(GlacierClient.builder()).build()) {
      client.listVaultsPaginator().vaultList().stream().forEach(vault -> {
        var data = new MagpieAwsResource.MagpieAwsResourceBuilder(mapper, vault.vaultARN())
          .withResourceName(vault.vaultName())
          .withResourceType(RESOURCE_TYPE)
          .withConfiguration(mapper.valueToTree(vault.toBuilder()))
          .withCreatedIso(Instant.parse(vault.creationDate()))
          .withSizeInBytes(vault.sizeInBytes())
          .withAccountId(account)
          .withAwsRegion(region.toString())
          .build();

        discoverJobs(client, vault, data);
        discoverMultipartUploads(client, vault, data);
        discoverAccessPolicy(client, vault, data);
        discoverVaultLock(client, vault, data);
        discoverVaultNotifications(client, vault, data);
        discoverTags(client, vault, data);

        emitter.emit(VersionedMagpieEnvelopeProvider.create(session, List.of(fullService() + ":vault"), data.toJsonNode()));
      });
    } catch (SdkServiceException | SdkClientException ex) {
      DiscoveryExceptions.onDiscoveryException(RESOURCE_TYPE, null, region, ex);
    }
  }

  private void discoverJobs(GlacierClient client, DescribeVaultOutput resource, MagpieAwsResource data) {
    final String keyname = "ListJobs";

    getAwsResponse(
      () -> client.listJobsPaginator(ListJobsRequest.builder().vaultName(resource.vaultName()).build()).jobList()
        .stream()
        .map(GlacierJobDescription::toBuilder)
        .collect(Collectors.toList()),
      (resp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, resp)),
      (noresp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, noresp))
    );
  }

  private void discoverMultipartUploads(GlacierClient client, DescribeVaultOutput resource, MagpieAwsResource data) {
    final String keyname = "MultipartUploads";

    getAwsResponse(
      () -> client.listMultipartUploadsPaginator(ListMultipartUploadsRequest.builder().vaultName(resource.vaultName()).build()).uploadsList()
        .stream()
        .map(UploadListElement::toBuilder)
        .collect(Collectors.toList()),
      (resp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, resp)),
      (noresp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, noresp))
    );
  }

  private void discoverAccessPolicy(GlacierClient client, DescribeVaultOutput resource, MagpieAwsResource data) {
    final String keyname = "AccessPolicy";

    getAwsResponse(
      () -> client.getVaultAccessPolicy(GetVaultAccessPolicyRequest.builder().vaultName(resource.vaultName()).build()),
      (resp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, resp)),
      (noresp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, noresp))
    );
  }

  private void discoverVaultNotifications(GlacierClient client, DescribeVaultOutput resource, MagpieAwsResource data) {
    final String keyname = "Notifications";

    getAwsResponse(
      () -> client.getVaultNotifications(GetVaultNotificationsRequest.builder().vaultName(resource.vaultName()).build()),
      (resp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, resp)),
      (noresp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, noresp))
    );
  }

  private void discoverVaultLock(GlacierClient client, DescribeVaultOutput resource, MagpieAwsResource data) {
    final String keyname = "VaultLock";

    getAwsResponse(
      () -> client.getVaultLock(GetVaultLockRequest.builder().vaultName(resource.vaultName()).build()),
      (resp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, resp)),
      (noresp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, noresp))
    );
  }

  private void discoverTags(GlacierClient client, DescribeVaultOutput resource, MagpieAwsResource data) {
    final String keyname = "Tags";

    getAwsResponse(
      () -> client.listTagsForVault(ListTagsForVaultRequest.builder().vaultName(resource.vaultName()).build()).tags(),
      (resp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, resp)),
      (noresp) -> AWSUtils.update(data.supplementaryConfiguration, Map.of(keyname, noresp))
    );
  }
}
