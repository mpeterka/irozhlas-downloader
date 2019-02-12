package cz.peterka.irozhlas;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {


    /**
     * Metoda pro spuštění
     *
     * @param args jeden parametr s URL s výpisem pořadů.
     * @throws Exception pri nejake chybe - error handling zde temer neni
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Předejte URL se výpisem pořadů. Např. https://hledani.rozhlas.cz/iRadio/?stanice%5B%5D=%C4%8CRo+Dvojka&porad%5B%5D=Klub+osam%C4%9Bl%C3%BDch+srdc%C3%AD+ser%C5%BEanta+Pep%C5%99e&zobrazNevysilane=0");
        }

        String baseUrl = args[0];
        if (baseUrl.contains("offset=")) {
            throw new IllegalArgumentException("Parametr offset sem nedávajte " + baseUrl);
        }
        int offset = 0;

        Document doc;
        do {
            doc = getDocument(baseUrl + "&offset=" + offset);
            final Elements playerLinks = doc.select("div.action-player a");// odkazy na prehrani.
            for (Element playerLink : playerLinks) {
                final String href = playerLink.attr("href");
                final String mediaLink = mediaLink(href);
                final String title = getTitle(playerLink);
                System.out.println("wget " + mediaLink + " -O '" + escapeFilename(title + ".mp3") + "'");
            }
            offset += 10;// strankovac je po deseti
        } while (!doc.select("a#sipka_right").isEmpty());

    }

    private static String escapeFilename(String title) {
        return title.replaceAll(":", ".")
                // nekdy je v titulku tecka, nekdy ne
                .replaceAll("\\.\\.mp3", ".mp3");
    }

    private static String getTitle(Element playerLink) {
        final Element parent = playerLink.parent().parent().parent();
        final String simple = parent.select(".title").text();
        if (!StringUtil.isBlank(simple)) {
            return simple;
        }
        return parent.select(".dateCover").text() + " " + parent.select("h3").text();
    }

    /**
     * Stahne dokument a naparsuje
     *
     * @param url url ke stazeni
     * @return parsovany dokument
     * @throws IOException pri cteni response
     */
    private static Document getDocument(String url) throws IOException {
        // vytvari se zde porad novy a novy klient...
        try (CloseableHttpClient client = getHttpClient()) {
            final CloseableHttpResponse response = client.execute(new HttpGet(url));
            final String content = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            return Jsoup.parse(content);
        }
    }

    /**
     * Pokusi se najit url audio souboru
     *
     * @param playerUrl odkaz na prehravac. Je jich vice typu
     * @return URL se zdrojovym audio souborem
     * @throws IOException pri cteni response
     */
    private static String mediaLink(String playerUrl) throws IOException {
        // napr. http://prehravac.rozhlas.cz/audio/4008200
        final Pattern p = Pattern.compile(".*/([0-9]*)");
        final Matcher m = p.matcher(playerUrl);
        if (m.matches() && m.groupCount() > 0) {
            final String mediaId = m.group(1);
            return String.format("http://media.rozhlas.cz/_audio/%s.mp3", mediaId);
        }
        // napr http://dvojka.rozhlas.cz/nahravky-joea-louise-walkera-hany-hegerove-jitky-suranske-skupiny-uz-jsme-doma-a-7700955?player=on#player
        // zde je url na soubor az na dalsi "detailu"
        final Document doc = getDocument(playerUrl);
        final Elements mp3links = doc.select("a[href*='.mp3']");
        return mp3links.stream().map(l -> l.attr("href")).findFirst().orElse(null);
    }

    private static CloseableHttpClient getHttpClient() {
        try {
            return HttpClients
                    .custom()
                    .setConnectionTimeToLive(10, TimeUnit.SECONDS)
                    // nechci zde resit certifikaty, vse akceptuji
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE).
                            setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, (TrustStrategy) (arg0, arg1) -> true).build())
                    .build();
        } catch (KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Nepodařilo se vytvořit http klienta: " + e.getMessage(), e);
        }
    }
}
