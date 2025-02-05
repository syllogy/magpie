package io.openraven.magpie.plugins.aws.discovery.services;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openraven.magpie.api.Emitter;
import io.openraven.magpie.api.MagpieAwsResource;
import io.openraven.magpie.api.MagpieEnvelope;
import io.openraven.magpie.plugins.aws.discovery.ClientCreators;
import io.openraven.magpie.plugins.aws.discovery.MagpieAWSClientCreator;
import io.openraven.magpie.plugins.aws.discovery.services.base.BaseAWSServiceIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeast;

@ExtendWith(MockitoExtension.class)
public class EC2DiscoveryIT extends BaseAWSServiceIT {

  private static final String CF_EC2_TEMPLATE_PATH = "/template/ec2-template.yml";
  private final EC2Discovery ec2Discovery = new EC2Discovery() {
    // We override this to make it a no-op since we can't perform Backup calls on the free version of Localstack.
    public void discoverBackupJobs(String arn, Region region, MagpieAwsResource data, MagpieAWSClientCreator clientCreator, Logger logger) {
    }
  };

  @Mock
  private Emitter emitter;

  @Captor
  private ArgumentCaptor<MagpieEnvelope> envelopeCapture;

  @BeforeAll
  public static void setup() {
    updateStackWithResources(CF_EC2_TEMPLATE_PATH);
  }

  public static MagpieAWSClientCreator localClientCreator(final Region region) {
    return new MagpieAWSClientCreator() {
      @Override
      public <BuilderT extends AwsClientBuilder<BuilderT, ClientT>, ClientT> BuilderT apply(AwsClientBuilder<BuilderT, ClientT> builder) {
        final var magpieAwsEndpoint = System.getProperty("MAGPIE_AWS_ENDPOINT");
        if (magpieAwsEndpoint != null) {
          builder.endpointOverride(URI.create(magpieAwsEndpoint));
        }
        return builder.region(region);
      }
    };
  }

  @Test
  public void testEC2Discovery() {

    // when
    ec2Discovery.discover(
      MAPPER,
      SESSION,
      BASE_REGION,
      emitter,
      LOGGER,
      ACCOUNT,
      ClientCreators.localClientCreator(BASE_REGION)
    );

    // then
    Mockito.verify(emitter, atLeast(1)).emit(envelopeCapture.capture());

    var resources = envelopeCapture.getAllValues().stream()
      .map(MagpieEnvelope::getContents)
      .collect(Collectors.groupingBy(val -> val.get("resourceType").asText()));

    assertInstance(resources.get("AWS::EC2::Instance"));
    assertEIP(resources.get("AWS::EC2::EIP"));
    assertSecurityGroup(resources.get("AWS::EC2::SecurityGroup"));
    assertVolume(resources.get("AWS::EC2::Volume"));
    assertSnapshot(resources.get("AWS::EC2::Snapshot"));
  }

  private void assertSnapshot(List<ObjectNode> data) {
    assertTrue(30 < data.size()); // Avoid flaky tests with backups generation
    var contents = data.get(0);

    assertNotNull(contents.get("documentId"));
    assertTrue(contents.get("arn").asText().startsWith("arn:aws:ec2:us-west-1:account:snapshot/snap-"));
    assertTrue(contents.get("resourceName").asText().startsWith("snap-"));
    assertEquals(contents.get("resourceName").asText(), contents.get("resourceId").asText());
    assertEquals("AWS::EC2::Snapshot", contents.get("resourceType").asText());
    assertEquals(ACCOUNT, contents.get("awsAccountId").asText());
    assertEquals(BASE_REGION.toString(), contents.get("awsRegion").asText());

    var configuration = contents.get("configuration");
    assertTrue(configuration.get("description").asText().startsWith("Auto-created snapshot for AMI ami-"));
    assertEquals("completed", configuration.get("state").asText());
    assertTrue(configuration.get("snapshotId").asText().startsWith("snap-"));
  }

  private void assertVolume(List<ObjectNode> data) {
    assertEquals(1, data.size());
    var contents = data.get(0);

    assertNotNull(contents.get("documentId"));
    assertTrue(contents.get("arn").asText().startsWith("arn:aws:ec2:us-west-1:account:volume/vol-"));
    assertTrue(contents.get("resourceName").asText().startsWith("vol-"));
    assertEquals(contents.get("resourceName").asText(), contents.get("resourceId").asText());
    assertEquals("AWS::EC2::Volume", contents.get("resourceType").asText());
    assertEquals(ACCOUNT, contents.get("awsAccountId").asText());
    assertEquals(BASE_REGION.toString(), contents.get("awsRegion").asText());

    var configuration = contents.get("configuration");
    assertTrue(configuration.get("volumeId").asText().startsWith("vol-"));
    assertEquals("gp2", configuration.get("volumeType").asText());

    var attachments = configuration.get("attachments");
    assertEquals(1, attachments.size());
    var attachment = attachments.get(0);
    assertNotNull(attachment.get("attachTime").asText());
    assertEquals("/dev/sda1", attachment.get("device").asText());
    assertTrue(attachment.get("instanceId").asText().startsWith("i-"));
    assertTrue(attachment.get("volumeId").asText().startsWith("vol-"));
    assertEquals("attached", attachment.get("state").asText());

  }

  private void assertSecurityGroup(List<ObjectNode> data) {
    assertEquals(1, data.size());
    var contents = data.get(0);

    assertNotNull(contents.get("documentId"));
    assertTrue(contents.get("arn").asText().startsWith("arn:aws:ec2:us-west-1:account:security-group/sg-"));
    assertEquals("default", contents.get("resourceName").asText());
    assertTrue(contents.get("resourceId").asText().startsWith("sg-"));
    assertEquals("AWS::EC2::SecurityGroup", contents.get("resourceType").asText());
    assertEquals(ACCOUNT, contents.get("awsAccountId").asText());
    assertEquals(BASE_REGION.toString(), contents.get("awsRegion").asText());

    var configuration = contents.get("configuration");
    assertEquals("default VPC security group", configuration.get("description").asText());
    assertEquals("default", configuration.get("groupName").asText());
    assertEquals("000000000000", configuration.get("ownerId").asText());
  }

  private void assertEIP(List<ObjectNode> data) {
    assertEquals(1, data.size());
    var contents = data.get(0);

    assertNotNull(contents.get("documentId"));
    assertEquals("arn:aws:ec2:us-west-1:account:eip-allocation/null", contents.get("arn").asText());
    // IP Address
    assertEquals(contents.get("resourceName").asText(), contents.get("configuration").get("publicIp").asText());
    assertEquals("AWS::EC2::EIP", contents.get("resourceType").asText());
    assertEquals(ACCOUNT, contents.get("awsAccountId").asText());
    assertEquals(BASE_REGION.toString(), contents.get("awsRegion").asText());

    var configuration = contents.get("configuration");
    assertTrue(configuration.get("instanceId").asText().startsWith("i-"));
    assertTrue(configuration.get("publicIp").asText().startsWith("127"));
    assertTrue(configuration.get("networkInterfaceId").asText().startsWith("eni-"));
    assertEquals("standard", configuration.get("domain").asText());
  }

  private void assertInstance(List<ObjectNode> data) {
    assertEquals(1, data.size());
    var contents = data.get(0);

    assertNotNull(contents.get("documentId"));
    assertTrue(contents.get("arn").asText().startsWith("arn:aws:ec2:us-west-1:000000000000:instance"));
    assertFalse(contents.get("resourceName").asText().isEmpty());
    assertEquals("AWS::EC2::Instance", contents.get("resourceType").asText());
    assertEquals(ACCOUNT, contents.get("awsAccountId").asText());
    assertEquals(BASE_REGION.toString(), contents.get("awsRegion").asText());

    var configuration = contents.get("configuration");
    assertTrue(configuration.get("imageId").asText().startsWith("ami-"));
    assertTrue(configuration.get("instanceId").asText().startsWith("i-"));
    assertEquals("m1.small", configuration.get("instanceType").asText());
    assertEquals("testkey", configuration.get("keyName").asText());
    assertNotNull(configuration.get("launchTime").asText());
    assertTrue(configuration.get("placement").get("availabilityZone").asText().startsWith(BASE_REGION.toString()));

    assertTrue(configuration.get("privateDnsName").asText().endsWith("us-west-1.compute.internal"));
    assertFalse(configuration.get("privateIpAddress").asText().isEmpty());
    assertTrue(configuration.get("publicDnsName").asText().endsWith("us-west-1.compute.amazonaws.com"));
    assertFalse(configuration.get("publicIpAddress").asText().isEmpty());

    var blockDeviceMappings = configuration.get("blockDeviceMappings");
    assertEquals(1, blockDeviceMappings.size());
    var device = blockDeviceMappings.get(0);
    assertEquals("/dev/sda1", device.get("deviceName").asText());
    assertEquals("in-use", device.get("ebs").get("status").asText());
    assertTrue(device.get("ebs").get("volumeId").asText().startsWith("vol-"));
    assertEquals("/dev/sda1", configuration.get("rootDeviceName").asText());
    assertEquals("ebs", configuration.get("rootDeviceType").asText());
    assertNotNull(configuration.get("publicIp").asText());
  }
}
