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
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openraven.magpie.api.Emitter;
import io.openraven.magpie.api.MagpieEnvelope;
import io.openraven.magpie.api.Session;
import io.openraven.magpie.plugins.aws.discovery.AWSUtils;
import org.slf4j.Logger;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.openraven.magpie.plugins.aws.discovery.AWSUtils.getAwsResponse;
import static java.util.Arrays.asList;

public class LambdaDiscovery implements AWSDiscovery {
  private static final String SERVICE = "lambda";

  @Override
  public String service() {
    return SERVICE;
  }

  private final List<LocalDiscovery> discoveryMethods = asList(
    this::discoverFunctionEventInvokeConfigs,
    this::discoverEventSourceMapping,
    this::discoverFunction,
    this::discoverFunctionInvokeConfig,
    this::discoverAccessPolicy
  );

  @FunctionalInterface
  interface LocalDiscovery {
    void discover(LambdaClient client, FunctionConfiguration resource, ObjectNode data, Logger logger, ObjectMapper mapper);
  }

  @Override
  public void discover(ObjectMapper mapper, Session session, Region region, Emitter emitter, Logger logger) {
    final var client = LambdaClient.builder().region(region).build();

    getAwsResponse(
      () -> client.listFunctionsPaginator().functions(),
      (resp) -> resp.forEach(function -> {
        var data = mapper.createObjectNode();
        data.putPOJO("configuration", function.toBuilder());
        data.put("region", region.toString());

        for (var dm : discoveryMethods)
          dm.discover(client, function, data, logger, mapper);

        emitter.emit(new MagpieEnvelope(session, List.of(fullService()), data));
      }),
      (noresp) -> logger.error("Failed to get functions in {}", region)
    );
  }

  private void discoverFunctionEventInvokeConfigs(LambdaClient client, FunctionConfiguration resource, ObjectNode data, Logger logger, ObjectMapper mapper) {
    final String keyname = "functionEventInvokeConfigs";
    getAwsResponse(
      () -> client.listFunctionEventInvokeConfigsPaginator(ListFunctionEventInvokeConfigsRequest.builder().functionName(resource.functionName()).build()).functionEventInvokeConfigs()
        .stream()
        .map(r -> r.toBuilder())
        .collect(Collectors.toList()),
      (resp) -> AWSUtils.update(data, Map.of(keyname, resp)),
      (noresp) -> AWSUtils.update(data, Map.of(keyname, noresp))
    );
  }

  private void discoverEventSourceMapping(LambdaClient client, FunctionConfiguration resource, ObjectNode data, Logger logger, ObjectMapper mapper) {
    final String keyname = "eventSourceMapping";
    getAwsResponse(
      () -> client.listEventSourceMappingsPaginator(ListEventSourceMappingsRequest.builder().functionName(resource.functionName()).build()).eventSourceMappings()
        .stream()
        .map(r -> r.toBuilder())
        .collect(Collectors.toList()),
      (resp) -> AWSUtils.update(data, Map.of(keyname, resp)),
      (noresp) -> AWSUtils.update(data, Map.of(keyname, noresp))
    );
  }

  private void discoverFunctionInvokeConfig(LambdaClient client, FunctionConfiguration resource, ObjectNode data, Logger logger, ObjectMapper mapper) {
    final String keyname = "functionInvokeConfig";
    getAwsResponse(
      () -> client.getFunctionEventInvokeConfig(GetFunctionEventInvokeConfigRequest.builder().functionName(resource.functionName()).build()),
      (resp) -> AWSUtils.update(data, Map.of(keyname, resp)),
      (noresp) -> AWSUtils.update(data, Map.of(keyname, noresp))
    );
  }

  private void discoverFunction(LambdaClient client, FunctionConfiguration resource, ObjectNode data, Logger logger, ObjectMapper mapper) {
    final String keyname = "function";
    getAwsResponse(
      () -> client.getFunction(GetFunctionRequest.builder().functionName(resource.functionName()).build()),
      (resp) -> AWSUtils.update(data, Map.of(keyname, resp)),
      (noresp) -> AWSUtils.update(data, Map.of(keyname, noresp))
    );
  }

  private void discoverAccessPolicy(LambdaClient client, FunctionConfiguration resource, ObjectNode data, Logger logger, ObjectMapper mapper) {
    final String keyname = "accessPolicy";
    getAwsResponse(
      () -> client.getPolicy(GetPolicyRequest.builder().functionName(resource.functionName()).build()),
      (resp) -> AWSUtils.update(data, Map.of(keyname, resp)),
      (noresp) -> AWSUtils.update(data, Map.of(keyname, noresp))
    );
  }
}