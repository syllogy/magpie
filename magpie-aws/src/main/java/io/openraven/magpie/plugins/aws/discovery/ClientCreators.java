package io.openraven.magpie.plugins.aws.discovery;

import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

public class ClientCreators {

  //This client does not need to be recreated on every request.
  public static final StsClient stsClient = StsClient.create();

  public static MagpieAWSClientCreator assumeRoleCreator(final Region region, final String roleArn, Optional<String> externalIdOptional) {
    return new MagpieAWSClientCreator(){
      @Override
      public <BuilderT extends AwsClientBuilder<BuilderT, ClientT>, ClientT> BuilderT apply(AwsClientBuilder<BuilderT, ClientT> builder) {
        final var magpieAwsEndpoint = System.getProperty("MAGPIE_AWS_ENDPOINT");
        if (magpieAwsEndpoint != null) {
          builder.endpointOverride(URI.create(magpieAwsEndpoint));
        }
          final AssumeRoleRequest.Builder assumeRoleRequestBuilder = AssumeRoleRequest.builder()
                  .roleArn(roleArn)
                  .roleSessionName(UUID.randomUUID().toString());
          externalIdOptional.ifPresent(assumeRoleRequestBuilder::externalId);
          final var provider = StsAssumeRoleCredentialsProvider.builder()
          .stsClient(stsClient)
          .refreshRequest(
            assumeRoleRequestBuilder
              .build()
          ).build();
        return builder.credentialsProvider(provider).region(region);
      }
    };
  }

  public static MagpieAWSClientCreator localClientCreator(final Region region) {
    return new MagpieAWSClientCreator(){
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
}
