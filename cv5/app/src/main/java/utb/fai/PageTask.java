package utb.fai;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class PageTask implements Runnable {

    private static final int TIMEOUT_MILLIS = 10_000;
    private static final String LINK_SELECTOR = "a[href], frame[src], iframe[src]";

    private final WebCrawler crawler;
    private final URIinfo page;

    PageTask(WebCrawler crawler, URIinfo page) {
        this.crawler = crawler;
        this.page = page;
    }

    @Override
    public void run() {
        try {
            crawler.acquireDownloadPermit();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            crawler.debug(1, "Analyzing " + page);
            Document document = download();
            if (document == null) {
                return;
            }
            Element body = document.body();
            if (body != null) {
                crawler.wordFrequency().addText(body.text());
            }
            if (crawler.shouldFollowLinksFrom(page.depth)) {
                scheduleLinks(document);
            }
        } catch (UnsupportedMimeTypeException e) {
            crawler.debug(1, "Skipping non-HTML content " + page.uri + " (" + e.getMimeType() + ")");
        } catch (HttpStatusException e) {
            crawler.debug(1, "HTTP " + e.getStatusCode() + " for " + page.uri);
        } catch (SocketTimeoutException e) {
            crawler.debug(1, "Timeout while loading " + page.uri);
        } catch (IOException e) {
            crawler.debug(1, "Unable to load " + page.uri + ": " + e);
        } catch (RuntimeException e) {
            crawler.debug(1, "Unable to process " + page.uri + ": " + e);
        } finally {
            crawler.releaseDownloadPermit();
        }
    }

    private Document download() throws IOException {
        Connection.Response response = Jsoup.connect(page.uri.toString())
                .timeout(TIMEOUT_MILLIS)
                .followRedirects(true)
                .execute();
        URI finalUri = WebCrawler.normalize(response.url().toString());
        if (finalUri != null && !finalUri.equals(page.uri) && !crawler.markVisited(finalUri)) {
            crawler.debug(1, page.uri + " redirects to already visited " + finalUri);
            return null;
        }
        return response.parse();
    }

    private void scheduleLinks(Document document) {
        for (Element link : document.select(LINK_SELECTOR)) {
            String attribute = link.hasAttr("href") ? "href" : "src";
            URI target = WebCrawler.normalize(link.absUrl(attribute));
            if (target != null) {
                crawler.schedule(target, page.depth + 1);
            }
        }
    }
}
