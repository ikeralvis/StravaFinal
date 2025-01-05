/**
 * This code is based on solutions provided by Claude Sonnet 3.5 and 
 * adapted using GitHub Copilot. It has been thoroughly reviewed 
 * and validated to ensure correctness and that it is free of errors.
 */
package es.deusto.sd.strava.client.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import es.deusto.sd.strava.client.data.Article;
import es.deusto.sd.strava.client.data.Category;
import es.deusto.sd.strava.client.data.Credentials;
import es.deusto.sd.strava.client.data.Entrenamiento;
import es.deusto.sd.strava.client.data.Reto;
import es.deusto.sd.strava.client.data.TokenPorID;
import es.deusto.sd.strava.client.data.Usuario;
import es.deusto.sd.strava.client.proxies.IAuctionsServiceProxy;
import es.deusto.sd.strava.client.proxies.IStravaServiceProxy;
import jakarta.servlet.http.HttpServletRequest;

/**
 * WebClientController class serves as the primary controller for the web client
 * application built with Spring Boot. It orchestrates the interactions between
 * the web application and the AuctionsService through the
 * RestTemplateServiceProxy, managing HTTP requests and responses while serving
 * Thymeleaf templates.
 * 
 * The use of the `@Controller` annotation in the WebClientController class
 * signifies that this class serves as a front controller in the Spring MVC
 * architecture. This annotation allows Spring to recognize and manage the class
 * as a web component, enabling it to handle HTTP requests and produce responses
 * based on user interactions.
 * 
 * Spring Boot's `@Controller` facilitates the use of model attributes through
 * the `Model` interface. The `model.addAttribute()` method is used to add
 * attributes to the model, making them accessible in the Thymeleaf templates.
 * This method takes a key-value pair, where the key is the name of the
 * attribute that can be referenced in the template, and the value is the actual
 * data to be passed. For instance, when `model.addAttribute("currentUrl",
 * currentUrl)` is called, the current URL is stored in the model with the key
 * "currentUrl", allowing it to be easily accessed in the corresponding
 * Thymeleaf view. This mechanism enables the dynamic rendering of content based
 * on the application state, ensuring that user interfaces are responsive and
 * adaptable to user interactions.
 * 
 * The methods of the controller return a `String`, which represents the name of
 * the Thymeleaf template to be rendered. This design pattern allows the
 * controller to define the appropriate view for each action. For instance, when
 * the `home` method is called, it returns the string "index", which tells
 * Spring to render the `index.html` Thymeleaf template. The mapping methods not
 * only process data but also dictate the presentation layer, facilitating a
 * clear separation between business logic and user interface concerns.
 * 
 * This class uses two distinct mappings to handle the login process, allowing
 * for a clear separation of responsibilities and improving code organization.
 * 
 * The `@GetMapping("/login")` method is responsible for displaying the login
 * page. This method prepares and returns the view containing the login form,
 * ensuring that users can easily access the interface needed to enter their
 * credentials.
 * 
 * On the other hand, the `@PostMapping("/login")` method handles the submission
 * of the form, processing user input, validating credentials, and managing the
 * authentication logic. This separation allows each method to have a single
 * responsibility, making the code easier to understand and maintain.
 * 
 * (Description generated with ChatGPT 4o mini)
 */
@Controller
public class StravaWebClientController {

	@Autowired
	private IStravaServiceProxy stravaServiceProxy;

	private String token; // Stores the session token

	// Add current URL and token to all views
	@ModelAttribute
	public void addAttributes(Model model, HttpServletRequest request) {
		String currentUrl = ServletUriComponentsBuilder.fromRequestUri(request).toUriString();
		model.addAttribute("currentUrl", currentUrl); // Makes current URL available in all templates
		model.addAttribute("token", token); // Makes token available in all templates
	}

	@GetMapping("/")
	public String home() {
		return "index";
	}

	@GetMapping("/register")
	public String showRegister(@RequestParam(value = "redirectUrl", required = false) String redirectUrl,
			Model model) {

		return "register";
	}

	@PostMapping("/register")
	public String performRegister(@RequestBody Usuario u, RedirectAttributes redirectAttributes) {
		try {
			// Llamada al servicio para agregar el entrenamiento
			stravaServiceProxy.registrar(u);

			// Si todo va bien, redirigimos a una página de éxito o a la vista de
			// entrenamiento del usuario
			redirectAttributes.addFlashAttribute("message", "Usuario registrado exitosamente");
			return "index";

		} catch (Exception e) {
			e.printStackTrace();
			redirectAttributes.addFlashAttribute("errorMessage", "Hubo un error en el registro");
			return "errorPage"; // Redirigir a la página del usuario
		}
	}

	@GetMapping("/login")
	public String showLoginPage(@RequestParam(value = "redirectUrl", required = false) String redirectUrl,
			Model model) {
		// Add redirectUrl to the model if needed
		model.addAttribute("redirectUrl", redirectUrl);

		return "login"; // Return your login template
	}

	@PostMapping("/login")
	public String performLogin(@RequestParam("email") String email, @RequestParam("password") String password,
			@RequestParam(value = "redirectUrl", required = false) String redirectUrl, Model model) {
		Credentials credentials = new Credentials(email, password);

		try {
			TokenPorID tokenId = stravaServiceProxy.login(credentials);

			// Redirect to the original page or root if redirectUrl is null
			return "redirect:" + (redirectUrl != null && !redirectUrl.isEmpty() ? redirectUrl : "/");
		} catch (RuntimeException e) {
			model.addAttribute("errorMessage", "Login failed: " + e.getMessage());
			return "login"; // Return to login page with error message
		}
	}

	@GetMapping("/logout")
	public String performLogout(@RequestParam(value = "redirectUrl", defaultValue = "/") String redirectUrl,
			Model model) {
		try {
			stravaServiceProxy.logout(token);
			token = null; // Clear the token after logout
			model.addAttribute("successMessage", "Logout successful.");
		} catch (RuntimeException e) {
			model.addAttribute("errorMessage", "Logout failed: " + e.getMessage());
		}

		// Redirect to the specified URL after logout
		return "redirect:" + redirectUrl;
	}

	@GetMapping("/usuarios/entrenamientos")
	public String conseguirEntrenamientosUsuario(
			@RequestParam(value = "startDate", required = false) LocalDate startDate,
			@RequestParam(value = "endDate", required = false) LocalDate endDate,
			Model model,
			RedirectAttributes redirectAttributes) {

		if (!estaLogeado(token))
			return "redirect:/login?redirectUrl=/usuarios/entrenamientos";

		try {
			// Llama al servicio proxy para obtener los entrenamientos del usuario
			List<Entrenamiento> trainings = stravaServiceProxy.consultarEntrenamientos(token, startDate, endDate);
			// Agrega los entrenamientos al modelo para mostrarlos en la vista
			model.addAttribute("trainings", trainings);
			model.addAttribute("startDate", startDate);
			model.addAttribute("endDate", endDate);

			return "entrenamientos"; // Thymeleaf renderiza "entrenamientos.html"
		} catch (RuntimeException e) {
			e.printStackTrace();
			redirectAttributes.addFlashAttribute("errorMessage",
					"Error al obtener los entrenamientos: " + e.getMessage());
			return "errorPage"; // Redirige a una página de error o a otra apropiada
		}

	}

	@PostMapping("/entretenamientos")
	public String anadirEntrenamientos(
			@RequestBody Entrenamiento entrenamiento,
			RedirectAttributes redirectAttributes) {

		try {
			// Llamada al servicio para agregar el entrenamiento
			stravaServiceProxy.anadirEntrenamiento(token, entrenamiento);

			// Redirigir con un mensaje de éxito
			redirectAttributes.addFlashAttribute("message", "Entrenamiento agregado exitosamente");
			return "redirect:/usuarios/entrenamientos"; // Redirigir a la página del usuario

		} catch (Exception e) {
			e.printStackTrace();
			redirectAttributes.addFlashAttribute("errorMessage", "Hubo un error al agregar el entrenamiento");

			return "errorPage"; // Redirigir a la página del usuario

		}
	}
	@PostMapping("/retos")
	public String anadirReto(
			@RequestBody Reto reto,
			RedirectAttributes redirectAttributes
			) {
			
	    try {
	        stravaServiceProxy.anadirReto(token, reto);

	        // Redirigir con un mensaje de éxito
	        redirectAttributes.addFlashAttribute("message", "Reto agregado exitosamente");
	        return "redirect:/challenges"; // Redirigir a la página del usuario

	    } catch (Exception e) {
	        e.printStackTrace();
	        redirectAttributes.addFlashAttribute("errorMessage", "Hubo un error al agregar el reto");
	        return "errorPage"; // Redirigir a la página del usuario
	    }			
	}

	public boolean estaLogeado(String token) {
		if (token != null) {
			return true;
		}
		return false;
	}
}