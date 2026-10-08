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
        // Keep desktop graphics enabled for local desktop folder pickers.
        SpringApplication app = new SpringApplication(KaiApplication.class);
        app.setHeadless(false);

        ConfigurableApplicationContext ctx;
        try {
            ctx = app.run(KaiConfig.springArgs(args,
                    KaiConfig.springCopy(KaiConfig.locate(args))));
        } catch (Exception e) {
            KaiConfig.exit("Kai could not start. The reason is shown above.");
            return;
        }

        // The server is running; open its actual port in the default Windows browser.
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
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return;
        }
        try {
            new ProcessBuilder("rundll32.exe", "url.dll,FileProtocolHandler", url).start();
        } catch (IOException e) {
            System.out.println("Could not open your browser automatically. Open " + url + " manually.");
        }
    }
}
