package it.izzonline.awsfacerekognitionspike;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.model.Attribute;
import com.amazonaws.services.rekognition.model.CompareFacesMatch;
import com.amazonaws.services.rekognition.model.CompareFacesRequest;
import com.amazonaws.services.rekognition.model.CompareFacesResult;
import com.amazonaws.services.rekognition.model.ComparedFace;
import com.amazonaws.services.rekognition.model.DetectFacesRequest;
import com.amazonaws.services.rekognition.model.DetectFacesResult;
import com.amazonaws.services.rekognition.model.FaceDetail;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectTaggingRequest;
import com.amazonaws.services.s3.model.GetObjectTaggingResult;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.Tag;
import com.amazonaws.util.IOUtils;

import it.izzonline.awsfacerekognitionspike.model.Person;

@Component
public class AwsFaceRekognitionUtils {

	private static final Logger logger = LoggerFactory.getLogger(AwsFaceRekognitionUtils.class);

	@Autowired
	private AmazonS3 s3Client;

	@Value("${similarity.threshold.percent}")
	private float similarityThresholdPercent;

	@Autowired
	private AmazonRekognition rekognitionClient;

	private static final String LAST_NAME = "lastName";
	private static final String FIRST_NAME = "firstName";

	public final String[] EXTENSIONS = new String[] { "gif", "png", "bmp", "jpg", "jpeg" };

	public final FilenameFilter IMAGE_FILTER = new FilenameFilter() {
		@Override
		public boolean accept(final File dir, final String name) {
			for (final String ext : EXTENSIONS) {
				if (name.endsWith("." + ext)) {
					return (true);
				}
			}
			return (false);
		}
	};

	public List<Person> findPersonThatMatchInAnS3Bucket(ByteBuffer sourceImageBytes, String photoBucket) {

		List<Person> possiblePerson = new ArrayList<Person>();
		Person person = new Person();

		ObjectListing objectListing = s3Client.listObjects(photoBucket);

		for (S3ObjectSummary os : objectListing.getObjectSummaries()) {
			String targetImage = os.getKey();

			Image source = new Image().withBytes(sourceImageBytes);
			Image target = new Image().withS3Object(new S3Object().withName(targetImage).withBucket(photoBucket));
			CompareFacesRequest request = new CompareFacesRequest().withSourceImage(source).withTargetImage(target)
					.withSimilarityThreshold(new Float(similarityThresholdPercent));

			CompareFacesResult compareFacesResult = rekognitionClient.compareFaces(request);

			List<CompareFacesMatch> faceDetails = compareFacesResult.getFaceMatches();

			for (CompareFacesMatch match : faceDetails) {

				ComparedFace face = match.getFace();

				if (face.getConfidence().floatValue() >= similarityThresholdPercent) {

					GetObjectTaggingRequest getTaggingRequest = new GetObjectTaggingRequest(photoBucket, targetImage);
					GetObjectTaggingResult getTagsResult = s3Client.getObjectTagging(getTaggingRequest);

					for (Tag tag : getTagsResult.getTagSet()) {
						if (tag.getKey().equalsIgnoreCase(FIRST_NAME)) {
							person.setFirstName(tag.getValue());
						}
						if (tag.getKey().equalsIgnoreCase(LAST_NAME)) {
							person.setLastName(tag.getValue());
						}
					}
					possiblePerson.add(person);
				}
			}
		}
		return possiblePerson;
	}

	public List<FaceDetail> getFacesFromPhoto(Image source) {
		DetectFacesRequest request = new DetectFacesRequest().withImage(source).withAttributes(Attribute.ALL);

		DetectFacesResult result = rekognitionClient.detectFaces(request);

		List<FaceDetail> faceDetails = result.getFaceDetails();
		return faceDetails;
	}

	public boolean findPersonThatMatchInAFolder(Image source, File dir) throws Exception {
		if (dir.isDirectory() && dir.listFiles(IMAGE_FILTER).length > 0) {
			for (final File f : dir.listFiles(IMAGE_FILTER)) {

				ByteBuffer targetImageBuffer = ByteBuffer.wrap(IOUtils.toByteArray(new FileInputStream(f)));

				Image target = new Image().withBytes(targetImageBuffer);

				CompareFacesRequest request = new CompareFacesRequest().withSourceImage(source).withTargetImage(target)
						.withSimilarityThreshold(new Float(similarityThresholdPercent));

				CompareFacesResult compareFacesResult = rekognitionClient.compareFaces(request);

				List<CompareFacesMatch> faceDetails = compareFacesResult.getFaceMatches();

				for (CompareFacesMatch match : faceDetails) {
					ComparedFace face = match.getFace();
					if (face.getConfidence().floatValue() >= similarityThresholdPercent) {
						logger.debug("Found known face in " + f.getName() + " image");
						return true;
					}
				}
			}
		} else {
			throw new Exception(dir + " is not a folder or doesn't contains any image file!");
		}
		return false;
	}

}
