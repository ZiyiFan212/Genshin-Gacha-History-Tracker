package fetcher;

import core.AuthkeyExpiredException;
import core.GachaServerConnectionException;
import core.InvalidAuthkeyUrlException;
import core.TooFrequentRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import utilities.AppLogger;
import model.GachaRecord;
import utilities.AppConstants;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Random;

/**
 * Partially refactored from the last version of genshin tracker.
 * <p>
 * Author: Frank
 */
public class Fetcher implements AutoCloseable {

    private static final int Page_Size = 20;
    private static final String[] Banner_Type =
            {
                    AppConstants.CHARACTER_EVENT_BANNER, AppConstants.CHARACTER_EVENT_BANNER2,
                    AppConstants.WEAPON_EVENT_BANNER, AppConstants.CHRONICLE_EVENT_BANNER,
                    AppConstants.STANDARD_EVENT_BANNER, AppConstants.NOVICE_EVENT_BANNER,
            };
    private static final int Page_Number = 1;
    private static final int Base_Delay = 300;
    private static final String apiDomainCN = "https://public-operation-hk4e.mihoyo.com";
    private String cleanQueryString = "";
    private static final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client;
    private static final Random rand = new Random();

    public Fetcher(String authKeyUrl) throws Exception {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        setKeys(authKeyUrl);
    }

    public static @NotNull String getUid() { return uid; }

    /**
     * Allowing the service layer to set the last pull ID. Called only if this user
     * has previously saved records.
     * @param endId last wish's pull ID.
     */
    public void setLastEndId(String endId) {
        this.lastEndId = endId;
    }

    @Override
    public void close() {
        if (client != null ) {
            client.close();
        }
    }

    public List<GachaRecord> getAllRecords() throws GachaServerConnectionException, AuthkeyExpiredException {
        List<GachaRecord> gachaRecords = new ArrayList<>();
        int currentBanner = 0;

        try {
            for (currentBanner = 0; currentBanner < Banner_Type.length; currentBanner++) {
                gachaRecords.addAll(fetchSingleBanner(Banner_Type[currentBanner]));
            }
            return gachaRecords;
        } catch (AuthkeyExpiredException e) {
            throw e;
        } catch (Exception e) {
            throw new GachaServerConnectionException(
                    "Error occurred in reading the banner: " + Banner_Type[currentBanner], e);
        } finally {
            uid = "";
            CALLED = false;
        }
    }

    private static final int MAX_RETRIES = 3;
    private static final int TOO_FREQUENT_DELAY_MS = 5000;
    private static boolean CALLED = false;
    private static String uid = "";
    private String lastEndId = null;

    /**
     * Fetch gacha records for a single banner
     *
     * @param bannerType Banner type, e.g., "301" (character wish), "302" (weapon wish)
     * @return List of gacha records for the banner
     * @throws IOException IO exception
     * @throws InterruptedException Thread interrupted exception
     * @throws GachaServerConnectionException Server connection exception
     * @throws AuthkeyExpiredException Authkey expired exception
     * @throws TooFrequentRequestException Too frequent request exception
     */
    private List<GachaRecord> fetchSingleBanner(String bannerType)
            throws IOException, InterruptedException, GachaServerConnectionException, AuthkeyExpiredException, TooFrequentRequestException {

        String endID = null;
        List<GachaRecord> currentBannerRecords = new ArrayList<>();
        if (lastEndId != null) endID = lastEndId;

        while (true) {
            int retries = 0;
            JsonNode jsonNode = null;

            while (retries < MAX_RETRIES) {
                try {
                    long currentTimestamp = System.currentTimeMillis() / 1000;
                    StringBuilder urlBuilder = new StringBuilder(apiDomainCN);
                    urlBuilder.append("/gacha_info/api/getGachaLog?")
                            .append(cleanQueryString)
                            .append("&t=").append(currentTimestamp)
                            .append("&timestamp=").append(currentTimestamp)
                            .append("&gacha_type=").append(bannerType)
                            .append("&page=").append(Page_Number)
                            .append("&size=").append(Page_Size);

                    if (endID != null && !endID.isEmpty()) {
                        urlBuilder.append("&end_id=").append(endID);
                    } else {
                        urlBuilder.append("&end_id=0");
                    }
                    AppLogger.INSTANCE.debug("uri: " + urlBuilder.substring(0, 20));

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(urlBuilder.toString()))
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
                            .header("Referer", "https://webstatic.mihoyo.com/")
                            .GET()
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    AppLogger.INSTANCE.debug("status code: " + response.statusCode() + "\n" + response.body().substring(0, 20));

                    if (response.statusCode() != 200) {
                        throw new GachaServerConnectionException(
                                "Error occurred in connecting with the server! Status: " + response.statusCode());
                    }

                    jsonNode = mapper.readTree(response.body());
                    if (jsonNode == null) throw new InvalidPropertiesFormatException("Returning json is empty");
                    JsonNode retCodeNode = jsonNode.get("retcode");
                    if (retCodeNode != null && retCodeNode.asInt() != 0) {
                        String message = jsonNode.has("message") ? jsonNode.get("message").asText() : "Unknown error";
                        AppLogger.INSTANCE.debug(message);
                        int retcode = retCodeNode.asInt();
                        if ("authkey timeout".equalsIgnoreCase(message) || "authkey error".equalsIgnoreCase(message)) {
                            throw new AuthkeyExpiredException(
                                    "Authkey expired or invalid. Please open wish history in-game again.");
                        }
                        if (retcode == -101 || message.contains("visit too frequently") || message.contains("too frequent")) {
                            throw new TooFrequentRequestException("Requests too frequent");
                        }

                        throw new GachaServerConnectionException(
                                "API Error - Retcode: " + retcode + ", Message: " + message);
                    }
                    break;

                } catch (TooFrequentRequestException e) {
                    retries++;
                    AppLogger.INSTANCE.warn("Too frequent request, retrying " + retries + "/" + MAX_RETRIES + "...");
                    Thread.sleep(TOO_FREQUENT_DELAY_MS * retries);
                    if (retries >= MAX_RETRIES) {
                        client.close();
                        throw e;
                    }
                }
            }

            JsonNode dataNode = jsonNode.get("data");
            JsonNode data = (dataNode != null) ? dataNode.get("list") : null;
            List<GachaRecord> pageRecords = new ArrayList<>();

            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    CALLED = CALLED || setUid(item);
                    GachaRecord rd = new GachaRecord(
                            item.get("gacha_type").asText(),
                            item.get("time").asText(),
                            item.get("name").asText(),
                            item.get("item_type").asText(),
                            item.get("item_id").asText(),
                            item.get("id").asText(),
                            item.get("rank_type").asInt()
                    );
                    pageRecords.add(rd);
                }
            }

            if (pageRecords.isEmpty()) {
                AppLogger.INSTANCE.debug("No more records for banner: " + bannerType);
                break;
            }

            currentBannerRecords.addAll(pageRecords);
            endID = pageRecords.getLast().getRecordID();
            Thread.sleep(Base_Delay + rand.nextInt(500, 1500));
        }
        AppLogger.INSTANCE.debug("Fetch record number: " + currentBannerRecords.size());
        return currentBannerRecords;
    }



    private boolean setUid(JsonNode item) {
        uid = item.get("uid").asText();
        return true;
    }

    private void setKeys(String authKeyUrl) throws InvalidAuthkeyUrlException {
        String decodedUrl = URLDecoder.decode(authKeyUrl, StandardCharsets.UTF_8);
        URI uri = URI.create(decodedUrl);

        String query = uri.getRawQuery();

        if (query == null || query.isEmpty()) {
            throw new InvalidAuthkeyUrlException(
                    "The query string extracted from the URL is null or empty.");
        }

        String[] pairs = query.split("&");
        StringBuilder cleanQueryBuilder = new StringBuilder();
        for (String pair : pairs) {
            if (!pair.startsWith("gacha_type=") && !pair.startsWith("page=") &&
                    !pair.startsWith("size=") && !pair.startsWith("end_id=") &&
                    !pair.startsWith("t=") && !pair.startsWith("_t=") &&
                    !pair.startsWith("timestamp=")) {
                if (!cleanQueryBuilder.isEmpty()) {
                    cleanQueryBuilder.append("&");
                }
                int equalsIndex = pair.indexOf("=");
                if (equalsIndex > 0) {
                    String key = pair.substring(0, equalsIndex);
                    String value = pair.substring(equalsIndex + 1);
                    // Must be UTF8 format
                    cleanQueryBuilder.append(key).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                } else {
                    cleanQueryBuilder.append(pair);
                }
            }
        }
        this.cleanQueryString = cleanQueryBuilder.toString();
    }
}