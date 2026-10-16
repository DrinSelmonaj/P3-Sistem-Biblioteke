# Sistem Biblioteke (P3)

Aplikacion CLI në Java për menaxhimin e një biblioteke: huazime, kthime, rezervime me radhë pritjeje, gjoba për vonesa, raporte dhe import librash nga [Open Library](https://openlibrary.org). Të dhënat ruhen në PostgreSQL përmes JDBC, me transaksione dhe `SELECT ... FOR UPDATE` që dy veprime njëkohësisht të mos e huazojnë të njëjtin artikull.

**Stack:** Java 25 · Maven · PostgreSQL · JDBC · jBCrypt · Jackson · JUnit 5

## Funksionalitetet

**Anëtari (Member)**
- Sign Up dhe login (fjalëkalimet ruhen si hash BCrypt)
- Huazim dhe kthim artikujsh (maksimumi 5 huazime aktive njëkohësisht, i bllokuar nëse ka gjoba të papaguara)
- Shikim dhe pagesë e gjobave
- Rezervime: kur artikulli kthehet, rezervimi i parë në radhë bëhet "gati për marrje"

**Bibliotekari (Librarian)**
- Regjistrim huazimi/kthimi për çdo anëtar
- Krijim gjobe për huazime të vonuara (0.50 € në ditë)
- Historia e pagesave dhe raporte
- Import librash nga Open Library (kërkim sipas titullit)

**Në sfond:** një scheduler kontrollon çdo 30 sekonda rezervimet "gati për marrje" që s'janë marrë brenda 3 ditëve, i anulon dhe ia kalon artikullin anëtarit tjetër në radhë.

## Arkitektura

```
Main (CLI, menu sipas rolit)
  └── service/   LoanService, FineService, ReservationService, ReportService, AuthService
        └── dao/      interface + Impl për çdo entitet (JDBC, PreparedStatement)
              └── util/DBConnection   lexon config.properties, lidhje e re për çdo thirrje
  └── client/    OpenLibraryClient (HttpClient + Jackson)
model/  LibraryItem → Book, DVD   ·   Person → Member, Librarian   ·   Loan, Fine, Reservation
```

Hierarkitë e trashëgimisë ruhen në DB me **Class Table Inheritance**: `library_items` + `books`/`dvds`, `persons` + `members`/`librarians`.

## Nisja

### 1. Databaza

```bash
createdb biblioteka
psql -d biblioteka -f db/schema.sql
```

### 2. Konfigurimi

```bash
cp src/main/resources/config.properties.example src/main/resources/config.properties
```

Plotëso `db.user` dhe `db.password`. `config.properties` është në `.gitignore` dhe nuk shkon në GitHub.

### 3. Llogaria e parë e bibliotekarit

Sign Up krijon vetëm anëtarë, kështu që bibliotekari i parë krijohet direkt në DB. `pgcrypto` prodhon hash BCrypt që e pranon jBCrypt:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO persons (id, name, email, phone, person_type, password_hash)
VALUES ('L001', 'Emri Mbiemri', 'email@shembull.com', NULL, 'LIBRARIAN', crypt('fjalekalimi', gen_salt('bf', 10)));
INSERT INTO librarians (person_id, employee_code) VALUES ('L001', 'EMP001');
```

### 4. Ekzekutimi

```bash
mvn compile exec:java -Dexec.mainClass=org.example.Main
```

Ose nga IntelliJ: `Main.java` → Run. Fjalëkalimi fshihet me `****` vetëm në një terminal të vërtetë, sepse paneli Run i IntelliJ s'ofron `System.console()`.

## Testet

```bash
mvn test
```

| Testi | Lloji | Çfarë vërteton |
|---|---|---|
| `DomainModelTest` | njësie | afati 21-ditor i librit, ditët e vonesës, pagesa e gjobës s'e bën balancën negative |
| `LoanServiceAuthorizationTest` | integrimi | një anëtar s'mund të kthejë huazimin e një anëtari tjetër |
| `DBConnectionTest` | integrimi | çdo thirrje merr lidhje të veçantë: mbyllja e njërës s'prish tjetrën |

Testet e integrimit kërkojnë PostgreSQL aktiv me `config.properties` të konfiguruar, dhe anëtarët `M001`, `M002` plus të paktën një libër të disponueshëm në DB.
