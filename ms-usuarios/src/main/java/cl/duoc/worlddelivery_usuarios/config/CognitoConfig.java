package cl.duoc.worlddelivery_usuarios.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration
public class CognitoConfig {
    @Bean(destroyMethod = "close")
    CognitoIdentityProviderClient cognito(@Value("${app.cognito.region}") String region) {
        return CognitoIdentityProviderClient.builder()
            .region(Region.of(region))
            .credentialsProvider(AwsCredentialsProviderChain.of(
                EnvironmentVariableCredentialsProvider.create(),
                InstanceProfileCredentialsProvider.create()))
            .httpClientBuilder(UrlConnectionHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(10)))
            .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(20)))
            .build();
    }
}
