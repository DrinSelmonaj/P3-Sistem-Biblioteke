package org.example.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// Pasqyron formen e pergjigjes JSON te Open Library — vetem per parsim,
// s'perdoret gjetke ne aplikacion (shih BookSearchResult per formen "e paster"
// qe perdor pjesa tjeter e kodit). @JsonIgnoreProperties(ignoreUnknown = true)
// eshte e domosdoshme: Open Library kthen shume fusha te tjera qe s'na duhen,
// pa kete Jackson do te hidhte exception per çdo fushe te panjohur.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenLibrarySearchResponse(
        @JsonProperty("numFound") int numFound,
        @JsonProperty("docs") List<Doc> docs) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Doc(
            @JsonProperty("title") String title,
            @JsonProperty("author_name") List<String> authorName,
            @JsonProperty("isbn") List<String> isbn,
            @JsonProperty("first_publish_year") Integer firstPublishYear) {}
}