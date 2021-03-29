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

package io.openraven.magpie.plugins.aws.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.openraven.magpie.api.Emitter;
import io.openraven.magpie.api.OriginPlugin;
import io.openraven.magpie.api.Session;
import io.openraven.magpie.plugins.aws.discovery.services.*;
import org.slf4j.Logger;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;

import java.util.List;
import java.util.stream.Collectors;

public class AWSDiscoveryPlugin implements OriginPlugin<AWSDiscoveryConfig> {

  protected static final ObjectMapper MAPPER = new ObjectMapper()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    .findAndRegisterModules();

  public final static String ID = "magpie.aws.discovery";

  private static final List<AWSDiscovery> DISCOVERY_LIST = List.of(
    new EC2Discovery(),
    new ECSDiscovery(),
    new S3Discovery(),
    new SNSDiscovery(),
    new RDSDiscovery(),
    new KMSDiscovery(),
    new VPCDiscovery());

  private Logger logger;
  private AWSDiscoveryConfig config;

  @Override
  public void discover(Session session, Emitter emitter) {
    final var enabledPlugins = DISCOVERY_LIST.stream().filter(p -> isEnabled(p.service())).collect(Collectors.toList());

    Ec2Client.create().describeRegions().regions().stream().map(r -> Region.of(r.regionName())).forEach(region -> enabledPlugins.forEach(d -> {
      try {
        d.discoverWrapper(MAPPER, session, region, emitter, logger);
      } catch (Exception ex) {
        logger.error("Discovery error", ex);
      }
    }));
  }

  private boolean isEnabled(String svc) {
    var enabled = config.getServices().isEmpty() || config.getServices().contains(svc);
    logger.debug("{} {} per config", enabled ? "Enabling" : "Disabling", svc);
    return enabled;
  }
  @Override
  public String id() {
    return ID;
  }

  @Override
  public void init(AWSDiscoveryConfig config, Logger logger) {
    this.logger = logger;
    this.config = config;
  }

  @Override
  public Class<AWSDiscoveryConfig> configType() {
    return AWSDiscoveryConfig.class;
  }
}