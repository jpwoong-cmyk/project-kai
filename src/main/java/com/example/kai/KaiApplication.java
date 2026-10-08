package com.example.kai;

import java.io.IOException;
import java.util.Locale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.example.kai.config.KaiConfig;

@SpringBootApplication
public class KaiApplication {

    public static void main(String[] args) {
        // Keep desktop graphics enabled for platforms that use Swing dialogs.
        SpringApplication app = new SpringApplication(KaiApplication.class);
        app.setHeadless(false);
	public static void main(String[] args) {
		// 1. Start Spring. kai.properties (if there is one) is loaded for server.port etc.
		// Kai's own settings and the AI connection are checked on the start page, not here,
		// so a missing or wrong file never stops Kai: the user fixes it in the browser.
		ConfigurableApplicationContext ctx;
		try {
SpringApplication app = new SpringApplication(KaiApplication.class);

// Allow native desktop folder selection dialogs.
app.setHeadless(false);

ctx = app.run(
    KaiConfig.springArgs(
        args,
        KaiConfig.springCopy(KaiConfig.locate(args))
    )
);

		}
		catch (Exception e) { // e.g. port already in use; Spring has already logged the details
			KaiConfig.exit("Kai could not start. The reason is shown above.");
			return;
		}

        ConfigurableApplicationContext ctx;
        try {
            ctx = app.run(KaiConfig.springArgs(args,
                    KaiConfig.springCopy(KaiConfig.locate(args))));
        } catch (Exception e) {
            KaiConfig.exit("Kai could not start. The reason is shown above.");
            return;
        }

        // Spring has finished starting the embedded web server at this point.
        String port = ctx.getEnvironment().getProperty("local.server.port",
                ctx.getEnvironment().getProperty("server.port", "8080"));
        String url = "http://localhost:" + port;
        System.out.println();
        System.out.println("Kai is running. Open " + url + " in your browser.");
        System.out.println("Keep this window open while you use Kai. Close it to stop Kai.");
        System.out.println();

        openWindowsBrowser(url);
    }

    private static void openWindowsBrowser(String url) {
        // Do not open a browser in remote Linux Codespaces or build environments.
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return;
        }
        try {
            // Windows opens the URL with the user's default browser.
            new ProcessBuilder("rundll32.exe", "url.dll,FileProtocolHandler", url).start();
        } catch (IOException e) {
            System.out.println("Could not open your browser automatically. Open " + url + " manually.");
        }
    }
}
