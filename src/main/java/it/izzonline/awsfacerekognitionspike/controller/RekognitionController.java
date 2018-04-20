package it.izzonline.awsfacerekognitionspike.controller;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.rekognition.model.FaceDetail;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.util.IOUtils;

import it.izzonline.awsfacerekognitionspike.AwsFaceRekognitionUtils;
import it.izzonline.awsfacerekognitionspike.model.Person;

@RestController
public class RekognitionController {

	@Value("${aws.s3.photo.bucket}")
	private String photoBucket;

	@Autowired
	AwsFaceRekognitionUtils awsFaceRekognitionUtils;

	@Value("${photo.folder}")
	private String photoFolder;

	@PostMapping("/faces")
	public List<FaceDetail> detectFacesFromFile(@RequestParam(name = "file") MultipartFile sourceImage)
			throws Exception {

		ByteBuffer sourceImageBytes = ByteBuffer.wrap(IOUtils.toByteArray(sourceImage.getInputStream()));

		Image source = new Image().withBytes(sourceImageBytes);

		List<FaceDetail> faceDetails = awsFaceRekognitionUtils.getFacesFromPhoto(source);

		return faceDetails;
	}

	@PostMapping("/compare-faces-s3")
	public Person compareFacesWithImagesInS3Bucket(@RequestParam(name = "file") MultipartFile sourceImage)
			throws Exception {

		ByteBuffer sourceImageBytes = ByteBuffer.wrap(IOUtils.toByteArray(sourceImage.getInputStream()));

		List<Person> possiblePerson = awsFaceRekognitionUtils.findPersonThatMatchInAnS3Bucket(sourceImageBytes,
				photoBucket);

		if (possiblePerson.isEmpty())
			throw new Exception("No matching face found");
		if (possiblePerson.size() > 1)
			throw new Exception("Too many matching faces found");

		return possiblePerson.get(0);
	}

	@PostMapping("/compare-faces")
	public boolean compareFacesWithImagesInFolder(@RequestParam(name = "file") MultipartFile sourceImage)
			throws Exception {

		ByteBuffer sourceImageBytes = ByteBuffer.wrap(IOUtils.toByteArray(sourceImage.getInputStream()));

		Image source = new Image().withBytes(sourceImageBytes);

		File dir = new File(photoFolder);

		return awsFaceRekognitionUtils.findPersonThatMatchInAFolder(source, dir);

	}

}
