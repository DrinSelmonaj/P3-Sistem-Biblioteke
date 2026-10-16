package org.example.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Kerkim librash ne <a href="https://openlibrary.org/developers/api">Open Library Search API</a>,
 * per importin e katalogut nga bibliotekari.
 */
public class OpenLibraryClient {

    private static final String BASE_URL = "https://openlibrary.org/search.json";

    // Qe nga Jan 2025, Open Library kerkon eksplicit fields=... — perndryshe
    // kthen nje grup te kufizuar automatikisht (ndryshim performance-i i tyre).
    private static final String FIELDS = "title,author_name,isbn,first_publish_year";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** Krijon klientin me timeout 5s per lidhjen dhe 10s per kerkesen. */
    public OpenLibraryClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * @param query titulli ose fjalet kyce
     * @return deri ne 10 rezultate; ISBN-ja mund te jete null nese Open Library s'e ka
     * @throws RuntimeException nese API kthen status tjeter nga 200 ose lidhja deshton
     */
    public List<BookSearchResult> search(String query) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = BASE_URL + "?q=" + encodedQuery + "&fields=" + FIELDS + "&limit=10";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                // Open Library kerkon User-Agent identifikues per perdorues
                // me vellim te ulet si ne — mirsjellje API-je, jo opsionale.
                .header("User-Agent", "P3-Sistem-Biblioteke-Student-Project/1.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Open Library API ktheu status " + response.statusCode());
            }

            OpenLibrarySearchResponse parsed = objectMapper.readValue(response.body(), OpenLibrarySearchResponse.class);

            List<BookSearchResult> results = new ArrayList<>();
            if (parsed.docs() != null) {
                for (OpenLibrarySearchResponse.Doc doc : parsed.docs()) {
                    String author = (doc.authorName() != null && !doc.authorName().isEmpty())
                            ? doc.authorName().get(0) : "Autor i panjohur";
                    // isbn mund te mungoje per disa rezultate — mbetet null,
                    // CLI do te kerkoje nga Librarian ta plotesoje manualisht
                    // (books.isbn eshte NOT NULL UNIQUE ne skeme).
                    String isbn = (doc.isbn() != null && !doc.isbn().isEmpty())
                            ? doc.isbn().get(0) : null;
                    results.add(new BookSearchResult(doc.title(), author, isbn, doc.firstPublishYear()));
                }
            }
            return results;

        } catch (IOException e) {
            throw new RuntimeException("Gabim gjate lidhjes me Open Library API", e);
        } catch (InterruptedException e) {
            // Rivendos flamurin "interrupted" — perndryshe kodi qe e thirri
            // s'e merr vesh qe thread-i u kerkua te ndalet.
            Thread.currentThread().interrupt();
            throw new RuntimeException("Kerkimi ne Open Library u nderpre", e);
        }
    }
}