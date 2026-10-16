package org.example.client;

/**
 * Nje rezultat i pastruar kerkimi nga Open Library, i pavarur nga forma e JSON-it.
 *
 * @param title            titulli
 * @param author           autori i pare, ose "Autor i panjohur"
 * @param isbn             ISBN-ja e pare, ose null
 * @param firstPublishYear viti i botimit te pare, ose null
 */
public record BookSearchResult(String title, String author, String isbn, Integer firstPublishYear) {}