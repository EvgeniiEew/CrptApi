package org.example;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CrptApi {
    private final HttpClient httpClient;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper objectMapper;
    private final int maxRetries;
    private final long retryDelayMillis;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this(timeUnit, requestLimit, 6, 1000);
    }

    public CrptApi(TimeUnit timeUnit, int requestLimit, int maxRetries, long retryDelayMillis) {
        this.httpClient = HttpClient.newBuilder().build();
        this.semaphore = new Semaphore(requestLimit);
        this.objectMapper = new ObjectMapper();
        this.maxRetries = maxRetries;
        this.retryDelayMillis = retryDelayMillis;

        this.scheduler = Executors.newScheduledThreadPool(1);
        this.scheduler.scheduleAtFixedRate(() -> semaphore.release(requestLimit - semaphore.availablePermits()),
                0, 1, timeUnit);
    }

    public void createDocument(Document document, String signature) throws InterruptedException, JsonProcessingException {
        String json = objectMapper.writeValueAsString(document);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        executeWithRetries(request);
    }

    private void executeWithRetries(HttpRequest request) throws InterruptedException {
        int attempts = 0;
        boolean success = false;
        while (attempts < maxRetries && !success) {
            attempts++;
            semaphore.acquire();
            try {
                HttpResponse<String> response = sendRequest(request);
                if (response.statusCode() == 200) {
                    success = true;
                } else {
                    System.err.println("Failed to execute request: " + response.body());
                    if (attempts < maxRetries) {
                        Thread.sleep(retryDelayMillis);
                    }
                }
            } catch (Exception e) {
                if (attempts >= maxRetries) {
                    throw new RuntimeException("Exception occurred while executing request after " + attempts + " attempts", e);
                }
                Thread.sleep(retryDelayMillis);
            }
        }

        if (!success) {
            throw new RuntimeException("Failed to execute request after " + maxRetries + " attempts");
        }
    }

    private HttpResponse<String> sendRequest(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public static class Description {
            public String participantInn;
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 5);
        Document document = new Document();
        document.doc_id = "example_doc_id";
        document.doc_status = "example_status";
        document.doc_type = "LP_INTRODUCE_GOODS";
        document.importRequest = true;
        document.owner_inn = "example_owner_inn";
        document.participant_inn = "example_participant_inn";
        document.producer_inn = "example_producer_inn";
        document.production_date = "2020-01-23";
        document.production_type = "example_production_type";
        document.reg_date = "2020-01-23";
        document.reg_number = "example_reg_number";
        document.description = new Document.Description();
        document.description.participantInn = "example_participant_inn";

        Document.Product product = new Document.Product();
        product.certificate_document = "example_certificate_document";
        product.certificate_document_date = "2020-01-23";
        product.certificate_document_number = "example_certificate_document_number";
        product.owner_inn = "example_owner_inn";
        product.producer_inn = "example_producer_inn";
        product.production_date = "2020-01-23";
        product.tnved_code = "example_tnved_code";
        product.uit_code = "example_uit_code";
        product.uitu_code = "example_uitu_code";

        document.products = new Document.Product[] { product };

        try {
            api.createDocument(document, "example_signature");
        } catch (InterruptedException | JsonProcessingException e) {
            e.printStackTrace();
        }

        api.shutdown();
    }
}