package search;

import model.DocumentData;

import java.util.*;

public class TFIDF {

    // term 占文章的比率
    public static double calculateTermFrequency(List<String> words, String term) {
        long count = 0;
        for (String word: words) {
            if (term.equalsIgnoreCase(word)) count++;
        }
        return (double) count / words.size();
    }

    // DocumentData 存放各个term的比率
    public static DocumentData createDocumentData(List<String> words, List<String> terms) {
        DocumentData documentData = new DocumentData();
        for (String term: terms) {
            double termFrequency = TFIDF.calculateTermFrequency(words, term.toLowerCase());
            documentData.putTermFrequency(term, termFrequency);
        }
        return documentData;
    }

    // 将一行的变成一个list
    public static List<String> getWordsFromLine(String line) {
        return Arrays.asList(line.split("(\\.)+|(,)+|( )+|(-)+|(\\?)+|(!)+|(;)+|(:)+|(/d)+|(/n)+"));
    }

    // 取文章中的所有单词
    public static List<String> getWordsFromDocument(List<String> lines) {
        List<String> words = new ArrayList<>();
        for (String line: lines) words.addAll(getWordsFromLine(line));
        return words;
    }

    private static double getInverseDocumentFrequency(String term, Map<String, DocumentData> documentResults) {
        double n = 0;
        for (String document: documentResults.keySet()) {
            DocumentData documentData = documentResults.get(document);
            double termFrequency = documentData.getFrequency(term);
            if (termFrequency > 0.0) n++;
        }
        return n == 0 ? 0 : Math.log10(documentResults.size() / n);
    }

    private static Map<String, Double> getTermToInverseDocumentFrequencyMap(
            List<String> terms, Map<String, DocumentData> documentResults) {
        Map<String, Double> termToIDF = new HashMap<>();
        for (String term: terms) {
            double idf = getInverseDocumentFrequency(term, documentResults);
            termToIDF.put(term, idf);
        }
        return termToIDF;
    }

    private static double calculateDocumentScore(List<String> terms, DocumentData documentData,
                                                 Map<String, Double> termToInverseDocumentFrequency) {
        double score = 0;
        for (String term: terms) {
            double termFrequency = documentData.getFrequency(term);
            double inverseTermFrequency = termToInverseDocumentFrequency.get(term);
            score += termFrequency * inverseTermFrequency;
        }
        return score;
    }

    private static void addDocumentScoreToTreeMap(TreeMap<Double,
            List<String>> scoreToDoc, double score, String document) {
        List<String> booksWithCurrentScore = scoreToDoc.get(score);
        if (booksWithCurrentScore == null) booksWithCurrentScore = new ArrayList<>();
        booksWithCurrentScore.add(document);
        scoreToDoc.put(score, booksWithCurrentScore);
    }

    public static Map<Double, List<String>> getDocumentsScores(
            List<String> terms, Map<String, DocumentData> documentResults) {
        TreeMap<Double, List<String>> scoreToDoc = new TreeMap<>();
        Map<String, Double> termToIDF = getTermToInverseDocumentFrequencyMap(terms, documentResults);
        for (String document : documentResults.keySet()) {
            DocumentData documentData = documentResults.get(document);
            double score = calculateDocumentScore(terms, documentData, termToIDF);
            addDocumentScoreToTreeMap(scoreToDoc, score, document);
        }
        return scoreToDoc.descendingMap();
    }
}
