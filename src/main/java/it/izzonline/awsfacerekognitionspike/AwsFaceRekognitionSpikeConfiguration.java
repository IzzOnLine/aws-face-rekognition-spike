package it.izzonline.awsfacerekognitionspike;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;

@Configuration
@EnableAutoConfiguration
public class AwsFaceRekognitionSpikeConfiguration {

	@Value("${aws.credential.username}")
	private String credentialUsername;

	@Value("${aws.rekognition.endpoint}")
	private String rekognitionEndpoint;

	@Bean
	public AWSCredentials credentials() {
		AWSCredentials credentials;
		try {
			credentials = new ProfileCredentialsProvider(credentialUsername).getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
					+ "Please make sure that your credentials file is at the correct "
					+ "location (/Users/userid.aws/credentials), and is in a valid format.", e);
		}
		return credentials;
	}

	@Bean
	public AmazonRekognition rekognitionClient() {
		EndpointConfiguration endpoint = new EndpointConfiguration(rekognitionEndpoint, Regions.EU_WEST_1.getName());
		AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.standard()
				.withEndpointConfiguration(endpoint).withCredentials(new AWSStaticCredentialsProvider(credentials()))
				.build();
		return rekognitionClient;
	}

}
