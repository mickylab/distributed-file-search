package search;

import cluster.management.ServiceRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import model.DocumentData;
import model.Result;
import model.SerializationUtils;
import model.Task;
import model.proto.SearchModel;
import networking.OnRequestCallback;
import networking.WebClient;
import org.apache.zookeeper.KeeperException;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class SearchCoordinator implements OnRequestCallback {

    // 统筹者的具体工作
    private static final String ENDPOINT = "/search";
    private static final String BOOKS_DIRECTORY = "./resources/books";
    private final ServiceRegistry workersServiceRegistry;
    private final WebClient webClient;
    private final List<String> documents;


    public SearchCoordinator(ServiceRegistry workersServiceRegistry, WebClient webClient) {
        this.workersServiceRegistry = workersServiceRegistry;
        this.webClient = webClient;
        this.documents = readDocumentsList();
    }

    @Override
    public byte[] handleRequest(byte[] requestPayload) {
        try {
            SearchModel.Request request = SearchModel.Request.parseFrom(requestPayload);
            SearchModel.Response response = createResponse(request);
            return response.toByteArray();
        } catch (InvalidProtocolBufferException | InterruptedException | KeeperException e) {
            e.printStackTrace();
            return SearchModel.Response.getDefaultInstance().toByteArray();
        }
    }

    @Override
    public String getEndpoint() {
        return ENDPOINT;
    }

    private SearchModel.Response createResponse(SearchModel.Request searchRequest) throws InterruptedException, KeeperException {
        SearchModel.Response.Builder searchResponse = SearchModel.Response.newBuilder();
        System.out.println("Received search query: " + searchRequest.getSearchQuery());
        List<String> searchTerms = TFIDF.getWordsFromLine(searchRequest.getSearchQuery());
        List<String> workers = workersServiceRegistry.getAllServiceAddresses();

        if (workers.isEmpty()) {
            System.out.println("No search workers currently available");
            return searchResponse.build();
        }
        // 有worker就可以分发任务了
        List<Task> tasks = createTasks(workers.size(), searchTerms);
        List<Result> results = sendTasksToWorkers(workers, tasks);

        List<SearchModel.Response.DocumentStats> sortedDocuments = aggregateResults(results, searchTerms);
        searchResponse.addAllRelevantDocuments(sortedDocuments);
        return searchResponse.build();
    }

    private List<SearchModel.Response.DocumentStats> aggregateResults(List<Result> results, List<String> terms) {
        Map<String, DocumentData> allDocumentsResults = new HashMap<>();
        for (Result result: results) allDocumentsResults.putAll(result.getDocumentToDocumentData());
        System.out.println("Calculating score for all the documents");
        Map<Double, List<String>> scoreToDocuments = TFIDF.getDocumentsScores(terms, allDocumentsResults);
        return sortDocumentsByScore(scoreToDocuments);
    }

    private List<SearchModel.Response.DocumentStats> sortDocumentsByScore(Map<Double, List<String>> scoreToDocuments) {
        List<SearchModel.Response.DocumentStats> sortedDocumentsStatsList = new ArrayList<>();
        for (Map.Entry<Double, List<String>> docScorePair: scoreToDocuments.entrySet()) {
            double score = docScorePair.getKey();
            for (String document: docScorePair.getValue()) {
                File documentPath = new File(document);
                SearchModel.Response.DocumentStats documentStats = SearchModel.Response.DocumentStats.newBuilder()
                        .setScore(score)
                        .setDocumentName(documentPath.getName())
                        .setDocumentSize(documentPath.length())
                        .build();
                sortedDocumentsStatsList.add(documentStats);
            }
        }
        return sortedDocumentsStatsList;
    }

    private List<Result> sendTasksToWorkers(List<String> workers, List<Task> tasks) {
        CompletableFuture<Result>[] futures = new CompletableFuture[workers.size()];
        for (int i = 0; i < workers.size(); i++) {
            String worker = workers.get(i);
            Task task = tasks.get(i);
            byte[] requestPayload = SerializationUtils.serialize(task);
            futures[i] = webClient.sendTask(worker, requestPayload);
        }

        List<Result> results = new ArrayList<>();
        for (CompletableFuture<Result> future: futures) {
            try {
                Result result = future.get();
                results.add(result);
            } catch (InterruptedException | ExecutionException ignored) {
            }
        }
        System.out.printf("Received %d/%d results%n", results.size(), tasks.size());
        return results;
    }

    // 为每个worker创建task(search terms 和 documents)
    public List<Task> createTasks(int numberOfWorkers, List<String> searchTerms) {
        List<List<String>> workersDocuments = splitDocumentList(numberOfWorkers, documents);
        List<Task> tasks = new ArrayList<>();
        for (List<String> documentsPerWorker: workersDocuments) {
            Task task = new Task(searchTerms, documentsPerWorker);
            tasks.add(task);
        }
        return tasks;
    }

    // 分配每一个worker的documents
    private static List<List<String>> splitDocumentList(int numberOfWorkers, List<String> documents) {
        int numberOfDocumentsPerWorker = (documents.size() + numberOfWorkers - 1) / numberOfWorkers;
        List<List<String>> workersDocuments = new ArrayList<>();

        for (int i = 0; i < numberOfWorkers; i++) {
            int leftDocumentIndex = i * numberOfDocumentsPerWorker;
            // 分配任务最后一个不能超过size
            int rightDocumentIndexExclusive = Math.min(leftDocumentIndex + numberOfDocumentsPerWorker, documents.size());
            if (leftDocumentIndex >= rightDocumentIndexExclusive) break;
            workersDocuments.add(new ArrayList<>(documents.subList(leftDocumentIndex, rightDocumentIndexExclusive)));
        }
        return workersDocuments;
    }

    // 读取文件名
    private static List<String> readDocumentsList() {
        File documentsDirectory = new File(BOOKS_DIRECTORY);
        return Arrays.stream(Objects.requireNonNull(documentsDirectory.list()))
                .map(documentName -> BOOKS_DIRECTORY + "/" + documentName)
                .collect(Collectors.toList());
    }
}
