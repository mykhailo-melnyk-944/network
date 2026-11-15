package utb.fai;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class App {

    private static final int DEFAULT_MAX_DEPTH = 2;
    private static final int DEFAULT_DEBUG_LEVEL = 0;
    private static final int REPORTED_WORD_COUNT = 20;

    public static void main(String[] args) {
        if (args.length < 1 || args[0].isBlank()) {
            System.err.println("Usage: java -jar app-all.jar <start URL> [max depth] [debug level]");
            return;
        }

        String startUrl = args[0].trim();
        int maxDepth = parseNonNegativeInt(args, 1, DEFAULT_MAX_DEPTH);
        int debugLevel = parseNonNegativeInt(args, 2, DEFAULT_DEBUG_LEVEL);

        WordFrequency wordFrequency = new WordFrequency();
        WebCrawler crawler = new WebCrawler(maxDepth, debugLevel, wordFrequency);

        try {
            crawler.crawl(startUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Crawling was interrupted, printing partial result");
        } catch (RuntimeException e) {
            System.err.println("Unexpected error while crawling: " + e);
        }

        printResult(wordFrequency.mostFrequent(REPORTED_WORD_COUNT));
    }

    private static int parseNonNegativeInt(String[] args, int index, int defaultValue) {
        if (args.length <= index) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(args[index].trim());
            if (value < 0) {
                System.err.println("Negative value '" + args[index] + "' replaced by " + defaultValue);
                return defaultValue;
            }
            return value;
        } catch (NumberFormatException e) {
            System.err.println("Invalid number '" + args[index] + "' replaced by " + defaultValue);
            return defaultValue;
        }
    }

    private static void printResult(List<Map.Entry<String, Integer>> words) {
        StringBuilder output = new StringBuilder();
        for (Map.Entry<String, Integer> word : words) {
            output.append(word.getKey()).append(';').append(word.getValue()).append(System.lineSeparator());
        }
        PrintStream utf8Out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        utf8Out.print(output);
        utf8Out.flush();
    }
}
