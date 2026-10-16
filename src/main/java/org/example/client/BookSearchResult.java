package org.example.client;

public record BookSearchResult(String title, String author, String isbn, Integer firstPublishYear) {}