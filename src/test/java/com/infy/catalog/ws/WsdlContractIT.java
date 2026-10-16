package com.infy.catalog.ws;

import com.infy.catalog.Application;
import com.infy.catalog.config.AppConfig;
import com.infy.catalog.infra.SoapRuntimeDefaults;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Contract guard (the SOAP equivalent of diffing an OpenAPI spec in CI).
 * Downloads the WSDL and XSDs the running service publishes, writes them to target/contract/,
 * and compares them with the committed baseline in src/test/resources/contract/.
 *
 * If the contract changed on purpose and the change is backward compatible (only additions),
 * run scripts/update-contract.sh and commit the new baseline with the code change.
 */
class WsdlContractIT {

    private static final Path OUTPUT = Path.of("target", "contract");
    private static final Path BASELINE = Path.of("src", "test", "resources", "contract");
    private static final Pattern XSD_REF = Pattern.compile("\\?xsd=(\\d+)");

    @Test
    void publishedContractMatchesBaseline() throws Exception {
        SoapRuntimeDefaults.configure();
        try (Application app = Application.start(AppConfig.fromEnvironment(
                Map.of("APP_PORT", "0", "SHUTDOWN_GRACE_SECONDS", "0")))) {
            String endpoint = "http://localhost:" + app.port() + "/ws/v1/products";
            HttpClient http = HttpClient.newHttpClient();

            Files.createDirectories(OUTPUT);
            String wsdl = fetch(http, endpoint + "?wsdl");
            Files.writeString(OUTPUT.resolve("ProductCatalogService.wsdl"), normalize(wsdl));

            TreeSet<String> schemaNumbers = new TreeSet<>();
            Matcher matcher = XSD_REF.matcher(wsdl);
            while (matcher.find()) {
                schemaNumbers.add(matcher.group(1));
            }
            for (String number : schemaNumbers) {
                String xsd = fetch(http, endpoint + "?xsd=" + number);
                Files.writeString(OUTPUT.resolve("ProductCatalogService-" + number + ".xsd"), normalize(xsd));
            }
        }

        assumeTrue(Files.isDirectory(BASELINE) && Files.exists(BASELINE.resolve("ProductCatalogService.wsdl")),
                "No contract baseline yet: run scripts/update-contract.sh and commit src/test/resources/contract/");

        assertEquals(listFiles(BASELINE), listFiles(OUTPUT), "Set of published contract files changed");
        for (String file : listFiles(OUTPUT)) {
            assertEquals(normalize(Files.readString(BASELINE.resolve(file))), Files.readString(OUTPUT.resolve(file)),
                    file + " changed. If intentional and backward compatible, run scripts/update-contract.sh");
        }
    }

    /** Removes what legitimately differs between runs: generator comments, host/port, whitespace. */
    static String normalize(String xml) {
        String withoutComments = xml.replaceAll("(?s)<!--.*?-->", "");
        String withoutHost = withoutComments.replaceAll("https?://[^/\"]+(/ws/v1/products)", "http://HOST$1");
        return withoutHost.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.joining("\n")) + "\n";
    }

    private static String fetch(HttpClient http, String url) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "GET " + url);
        return response.body();
    }

    private static TreeSet<String> listFiles(Path directory) {
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".wsdl") || name.endsWith(".xsd"))
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
