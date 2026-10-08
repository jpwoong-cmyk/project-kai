package com.example.kai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.example.kai.config.KaiConfig;

@SpringBootApplication
public class KaiApplication {

	public static void main(String[] args) {
		// 1. Start Spring. kai.properties (if there is one) is loaded for server.port etc.
		// Kai's own settings and the AI connection are checked on the start page, not here,
		// so a missing or wrong file never stops Kai: the user fixes it in the browser.
		ConfigurableApplicationContext ctx;
		try {
			ctx = new SpringApplication(KaiApplication.class)
					.run(KaiConfig.springArgs(args, KaiConfig.springCopy(KaiConfig.locate(args))));
		}
		catch (Exception e) { // e.g. port already in use; Spring has already logged the details
			KaiConfig.exit("Kai could not start. The reason is shown above.");
			return;
		}

		// 2. Tell the user where to go
		String url = "http://localhost:" + ctx.getEnvironment().getProperty("local.server.port");
		System.out.println();
		System.out.println("Kai is running. Open " + url + " in your browser.");
		System.out.println("Keep this window open while you use Kai. Close it to stop Kai.");
		System.out.println();
	}
}
