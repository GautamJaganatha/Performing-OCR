package com.ToOCR.OCR.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentMetricsService {
    private static final Logger log = LoggerFactory.getLogger(DocumentMetricsService.class);

    public Map<String, Object> calculateMetrics(String text) {
        log.info("Starting metrics calculation");
        Map<String, Object> metrics = new HashMap<>();

        try {
            // Calculate total words
            String[] words = text.toLowerCase()
                    .replaceAll("[^a-zA-Z ]", " ")
                    .split("\\s+");
            log.debug("Text split into {} words", words.length);

            // Count word frequency
            Map<String, Long> wordFrequency = Arrays.stream(words)
                    .filter(word -> !word.isEmpty())
                    .collect(Collectors.groupingBy(
                            Function.identity(),
                            Collectors.counting()
                    ));
            log.debug("Word frequency map created with {} unique words", wordFrequency.size());

            // Get top 10 words
            List<Map.Entry<String, Long>> topWords = wordFrequency.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .collect(Collectors.toList());
            log.debug("Top 10 words identified: {}", topWords);

            metrics.put("totalWords", words.length);
            metrics.put("topWords", topWords);

            log.info("Metrics calculation completed successfully");
        } catch (Exception e) {
            log.error("Error calculating document metrics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to calculate document metrics", e);
        }

        return metrics;
    }
}
