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

	@Value("${photo.folder}")
	private String photoFolder;

	static final String[] EXTENSIONS = new String[] { "gif", "png", "bmp", "jpg", "jpeg" };
	
	private static final Logger logger = LoggerFactory.getLogger(RekognitionController.class);	


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
		String targetImage = "";

		ByteBuffer sourceImageBytes = ByteBuffer.wrap(IOUtils.toByteArray(sourceImage.getInputStream()));
		ObjectListing objectListing = s3Client.listObjects(photoBucket);

		// confronta con tutte le immagini presenti nel bucket s3
		for (S3ObjectSummary os : objectListing.getObjectSummaries()) {
			targetImage = os.getKey();

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

		if (possiblePerson.isEmpty())
			throw new Exception("No matching face found");
		if (possiblePerson.size() > 1)
			throw new Exception("Too many matching faces found");

		return person;
	}

	@PostMapping("/compare-faces")
	public boolean compareFacesWithImagesInFolder(@RequestParam(name = "file") MultipartFile sourceImage)
			throws Exception {

		ByteBuffer sourceImageBytes = ByteBuffer.wrap(IOUtils.toByteArray(sourceImage.getInputStream()));

		Image source = new Image().withBytes(sourceImageBytes);

		File dir = new File(photoFolder);

		if (dir.isDirectory() && dir.listFiles(IMAGE_FILTER).length>0) {
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
						logger.debug("Found known face in "+f.getName() +" image");
						return true;
					}
				}
			}
		}else {
			throw new Exception(dir + " is not a folder or doesn't contains any image file!");
		}
		return false;
	}

	static final FilenameFilter IMAGE_FILTER = new FilenameFilter() {
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

}
