package it.izzonline.awsfacerekognitionspike;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MvcController {

	@GetMapping("/")
	public String getIndex(Model model) {
		return "index";
	}

}
