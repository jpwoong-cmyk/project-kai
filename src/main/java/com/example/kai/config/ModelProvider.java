package com.example.kai.config;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// The AI connection. Agents ask it for a client on every call, so a model chosen on the start
// page is used at once. Setup decides when to switch (only to a model that passed connect()).
// OpenAI-compatible services only (for now).
@Component
public class ModelProvider {

	public static final String KEY = "spring.ai.openai.api-key";
	public static final String URL = "spring.ai.openai.base-url";
	public static final String MODEL = "spring.ai.openai.chat.model";

	private static final String ADMIN = "\nCheck the values with your Kai admin.";

	public record Settings(String url, String key, String model) {
	}

	public static class Problem extends Exception {

		Problem(String message) {
			super(message);
		}
	}

	private volatile Settings settings; // null until the user clicks Start Kai on the start page
	private volatile ChatModel chatModel;

	public Settings settings() {
		return settings;
	}

	public boolean ready() {
		return chatModel != null;
	}

	public ChatClient client(String system) {
		ChatModel m = chatModel;
		if (m == null) {
			throw new IllegalStateException("The AI connection is not set up. Open the start page to fix it.");
		}
		return ChatClient.builder(m).defaultSystem(system).build();
	}

	void use(Settings s, ChatModel m) {
		chatModel = m;
		settings = s;
	}

	// The models this key may use, sorted. Empty = the service does not list its models,
	// so the user types the name.
	public List<String> models(String url, String key) throws Problem {
		url = checkUrlAndKey(url, key);
		var timeouts = new SimpleClientHttpRequestFactory();
		timeouts.setConnectTimeout(Duration.ofSeconds(10));
		timeouts.setReadTimeout(Duration.ofSeconds(30));
		try {
			ModelList list = RestClient.builder().requestFactory(timeouts).build()
					.get().uri(url + "/models").header("Authorization", "Bearer " + key)
					.retrieve().body(ModelList.class);
			return list == null || list.data() == null ? List.of()
					: list.data().stream().map(Entry::id).filter(id -> id != null && !id.isBlank()).distinct().sorted().toList();
		}
		catch (RestClientResponseException e) {
			if (e.getStatusCode().value() == 404 || e.getStatusCode().value() == 405) {
				return List.of();
			}
			throw new Problem(status(e.getStatusCode().value(), url, null, e.getMessage()));
		}
		catch (ResourceAccessException e) {
			throw new Problem(unreachable(url));
		}
		catch (RuntimeException e) { // e.g. the answer is not the expected JSON
			return List.of();
		}
	}

	// Build the model and make one tiny call with it
	ChatModel connect(Settings s) throws Problem {
		String url = checkUrlAndKey(s.url(), s.key());
		if (s.model() == null || s.model().isBlank()) {
			throw new Problem("No AI model is chosen." + ADMIN);
		}
		ChatModel m = OpenAiChatModel.builder()
				.options(OpenAiChatOptions.builder().baseUrl(url).apiKey(s.key()).model(s.model()).build())
				.build();
		try {
			ChatClient.create(m).prompt().user("Reply with the word OK.").call().content();
			return m;
		}
		catch (Exception e) {
			String why = explain(e, url, s.model());
			if (why == null) {
				return m; // 429: key and address work, the service is just busy
			}
			throw new Problem(why);
		}
	}

	// OpenAI SDK only appends /chat/completions, so the address must end with /v1: add it if missing
	public static String normalize(String url) {
		String u = url == null ? "" : url.trim().replaceAll("/+$", "");
		return u.isEmpty() || u.endsWith("/v1") ? u : u + "/v1";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ModelList(List<Entry> data) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Entry(String id) {
	}

	private static String checkUrlAndKey(String url, String key) throws Problem {
		if (url == null || url.isBlank()) {
			throw new Problem("The AI address is not set." + ADMIN);
		}
		if (key == null || key.isBlank()) {
			throw new Problem("The AI key is not set." + ADMIN);
		}
		url = normalize(url);
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			throw new Problem("The AI address must start with https://  but is: " + url + ADMIN);
		}
		return url;
	}

	private static String explain(Exception e, String url, String model) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof OpenAIServiceException s) {
				return s.statusCode() == 429 ? null : status(s.statusCode(), url, model, s.getMessage());
			}
			if (t instanceof OpenAIIoException) {
				return unreachable(url);
			}
		}
		return "Kai could not talk to the AI service: " + e.getMessage() + ADMIN;
	}

	private static String status(int code, String url, String model, String detail) {
		return switch (code) {
			case 401, 403 -> "The AI service refused the key. It is wrong or has expired." + ADMIN;
			case 404 -> "The AI service does not know this address" + (model == null ? "" : " or model") + ":\n  " + url
					+ (model == null ? "" : "\n  model: " + model) + ADMIN;
			default -> "The AI service answered with an error (code " + code + "): " + detail + ADMIN;
		};
	}

	private static String unreachable(String url) {
		return "Kai cannot reach the AI service at " + url
				+ "\nCheck that you are online (and on the company VPN, if you need one), and that the address is right.";
	}
}
