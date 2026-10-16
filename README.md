# Sistem Bibliotekë

Aplikacion konsol në Java për menaxhimin e një biblioteke, me databazë PostgreSQL. Ka dy role: anëtarë dhe bibliotekarë, me hyrje të sigurt (fjalëkalimet ruhen me BCrypt).

## Stack
- **Gjuha:** Java 25
- **Build:** Maven
- **Databaza:** PostgreSQL (JDBC)
- **Siguria:** jBCrypt
- **Testet:** JUnit 5

## Funksionalitetet

**Anëtari**
- Regjistrim dhe hyrje
- Shikon huazimet aktive, huazon dhe kthen artikuj (libra dhe DVD)
- Shikon dhe paguan gjobat
- Shikon dhe anulon rezervimet

**Bibliotekari**
- Regjistron huazime dhe kthime për anëtarët
- Krijon gjoba për huazimet e vonuara
- Shikon historinë e plotë të pagesave

Rezervimet e skaduara kontrollohen automatikisht nga një scheduler.

## Arkitektura
```
src/main/java/org/example/
├── model/     # Entitetet: LibraryItem → Book, DVD; Person → Member, Librarian; Loan, Fine, Reservation
├── dao/       # Qasja në databazë (interface + implementim për çdo entitet)
├── service/   # Logjika: AuthService, LoanService, FineService, ReservationService
├── util/      # DBConnection, CategoryParser
└── Main.java  # Menutë e konsolës
```

Skema e databazës (`db/schema.sql`) përdor **Class Table Inheritance**: `library_items` me `books`/`dvds`, dhe `persons` me anëtarët/bibliotekarët.

## Instalimi
1. Krijo databazën dhe ekzekuto skemën:
   ```bash
   psql -d biblioteka -f db/schema.sql
   ```
2. Krijo `src/main/resources/config.properties` (nuk ruhet në Git):
   ```properties
   db.url=jdbc:postgresql://localhost:5432/biblioteka
   db.user=postgres
   db.password=fjalekalimi_yt
   ```
3. Ndërto projektin dhe ekzekuto `org.example.Main` nga IntelliJ IDEA:
   ```bash
   mvn compile
   ```

## Testet
```bash
mvn test
```
