package utb.fai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WordFrequency {

    private static final String WHITESPACE = "\\s+";

    private final ConcurrentHashMap<String, Integer> occurrences = new ConcurrentHashMap<>();

    public void addText(String text) {
        Map<String, Integer> pageOccurrences = new HashMap<>();
        for (String word : text.split(WHITESPACE)) {
            if (!word.isEmpty()) {
                pageOccurrences.merge(word, 1, Integer::sum);
            }
        }
        pageOccurrences.forEach((word, count) -> occurrences.merge(word, count, Integer::sum));
    }

    public List<Map.Entry<String, Integer>> mostFrequent(int limit) {
        Map<String, Integer> snapshot = new HashMap<>();
        occurrences.forEach(snapshot::put);

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(snapshot.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }
}
