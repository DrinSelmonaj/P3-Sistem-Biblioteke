package org.example.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Pasqyron formen e pergjigjes JSON te Open Library — vetem per parsim, s'perdoret gjetke
 * (shih {@link BookSearchResult} per formen e paster). {@code ignoreUnknown = true} eshte e
 * domosdoshme: Open Library kthen shume fusha te tjera qe s'na duhen, dhe pa te Jackson
 * do te hidhte exception per cdo fushe te panjohur.
 *
 * @param numFound sa rezultate gjithsej gjeti API
 * @param docs     rezultatet e kesaj faqeje
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenLibrarySearchResponse(
        @JsonProperty("numFound") int numFound,
        @JsonProperty("docs") List<Doc> docs) {

    /**
     * Nje liber nga lista {@code docs}.
     *
     * @param title            titulli
     * @param authorName       autoret
     * @param isbn             ISBN-te e te gjitha botimeve
     * @param firstPublishYear viti i botimit te pare
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Doc(
            @JsonProperty("title") String title,
            @JsonProperty("author_name") List<String> authorName,
            @JsonProperty("isbn") List<String> isbn,
            @JsonProperty("first_publish_year") Integer firstPublishYear) {}
}