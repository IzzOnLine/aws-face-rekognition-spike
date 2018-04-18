package it.izzonline.awsfacerekognitionspike;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

@RestController
public class RekognitionController {

	@Value("${aws.s3.photo.bucket}")
	private String photoBucket;

	@Value("${similarity.threshold.percent}")
	private float similarityThresholdPercent;

	@Autowired
	private AmazonRekognition rekognitionClient;

	@Autowired
	private AmazonS3 s3Client;

	private static final String LAST_NAME = "lastName";
	private static final String FIRST_NAME = "firstName";

	@PostMapping("/faces")
	public List<FaceDetail> detectFacesFromFile(@RequestParam(name = "file") MultipartFile sourceImage)
			throws Exception {

		List<FaceDetail> faceDetails = new ArrayList<FaceDetail>();
		ByteBuffer sourceImageBytes = null;

		sourceImageBytes = ByteBuffer.wrap(IOUtils.toByteArray(sourceImage.getInputStream()));

		Image source = new Image().withBytes(sourceImageBytes);

		DetectFacesRequest request = new DetectFacesRequest().withImage(source).withAttributes(Attribute.ALL);

		DetectFacesResult result = rekognitionClient.detectFaces(request);
		faceDetails = result.getFaceDetails();

		return faceDetails;
	}

	@PostMapping("/compare-faces-s3")
	public Person compareFacesWithImagesInS3Bucket(@RequestParam(name = "file") MultipartFile sourceImage)
			throws Exception {

		List<Person> possiblePerson = new ArrayList<Person>();
		Person person = new Person();
		Float similarityThreshold = new Float(similarityThresholdPercent);
		String targetImage = "";

		ByteBuffer sourceImageBytes = null;

		sourceImageBytes = ByteBuffer.wrap(IOUtils.toByteArray(sourceImage.getInputStream()));
		ObjectListing objectListing = s3Client.listObjects(photoBucket);

		// confronta con tutte le immagini presenti nel bucket s3
		for (S3ObjectSummary os : objectListing.getObjectSummaries()) {
			targetImage = os.getKey();

			Image source = new Image().withBytes(sourceImageBytes);
			Image target = new Image().withS3Object(new S3Object().withName(targetImage).withBucket(photoBucket));
			CompareFacesRequest request = new CompareFacesRequest().withSourceImage(source).withTargetImage(target)
					.withSimilarityThreshold(similarityThreshold);

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

		if (!possiblePerson.isEmpty() && possiblePerson.size() == 1) {
			return person;
		} else {
			throw new Exception();
		}
	}

	// @PostMapping("/compare-faces")
	// public void compareFaces() {
	// Float similarityThreshold = 70F;
	// String sourceImage = "/Users/st3fania/Downloads/stefy.jpeg";
	// String targetImage = "/Users/st3fania/Downloads/stefy2.jpeg";
	// ByteBuffer sourceImageBytes = null;
	// ByteBuffer targetImageBytes = null;
	//
	// // Load source and target images and create input parameters
	// try (InputStream inputStream = new FileInputStream(new File(sourceImage))) {
	// sourceImageBytes = ByteBuffer.wrap(IOUtils.toByteArray(inputStream));
	// } catch (Exception e) {
	// System.out.println("Failed to load source image " + sourceImage);
	// System.exit(1);
	// }
	// try (InputStream inputStream = new FileInputStream(new File(targetImage))) {
	// targetImageBytes = ByteBuffer.wrap(IOUtils.toByteArray(inputStream));
	// } catch (Exception e) {
	// System.out.println("Failed to load target images: " + targetImage);
	// System.exit(1);
	// }
	//
	// Image source = new Image().withBytes(sourceImageBytes);
	// Image target = new Image().withBytes(targetImageBytes);
	//
	// CompareFacesRequest request = new
	// CompareFacesRequest().withSourceImage(source).withTargetImage(target)
	// .withSimilarityThreshold(similarityThreshold);
	//
	// // Call operation
	// CompareFacesResult compareFacesResult =
	// rekognitionClient.compareFaces(request);
	//
	// // Display results
	// List<CompareFacesMatch> faceDetails = compareFacesResult.getFaceMatches();
	// for (CompareFacesMatch match : faceDetails) {
	// ComparedFace face = match.getFace();
	// BoundingBox position = face.getBoundingBox();
	// System.out.println("Face at " + position.getLeft().toString() + " " +
	// position.getTop() + " matches with "
	// + face.getConfidence().toString() + "% confidence.");
	//
	// }
	// List<ComparedFace> uncompared = compareFacesResult.getUnmatchedFaces();
	//
	// System.out.println("There were " + uncompared.size() + " that did not
	// match");
	// System.out.println("Source image rotation: " +
	// compareFacesResult.getSourceImageOrientationCorrection());
	// System.out.println("target image rotation: " +
	// compareFacesResult.getTargetImageOrientationCorrection());
	// }

}
