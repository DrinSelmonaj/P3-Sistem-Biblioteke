# Sistem Biblioteke (P3)

Aplikacion CLI në Java për menaxhimin e një biblioteke: huazime, kthime, rezervime me radhë pritjeje, gjoba për vonesa, raporte dhe import librash nga [Open Library](https://openlibrary.org). Të dhënat ruhen në PostgreSQL përmes JDBC, me transaksione dhe `SELECT ... FOR UPDATE` që dy veprime njëkohësisht të mos e huazojnë të njëjtin artikull.

**Stack:** Java 25 · Maven · PostgreSQL · JDBC · jBCrypt · Jackson · JUnit 5

## Funksionalitetet

**Anëtari (Member)**
- Sign Up dhe login (fjalëkalimet ruhen si hash BCrypt, minimumi 8 karaktere)
- Huazim dhe kthim artikujsh (maksimumi 5 huazime aktive njëkohësisht, i bllokuar nëse ka gjoba të papaguara)
- Shikim dhe pagesë e gjobave
- Rezervim i artikujve të zënë, me radhë FIFO: kur artikulli kthehet, i pari në radhë e merr "gati për marrje"
- Shikim dhe anulim i rezervimeve

**Bibliotekari (Librarian)**
- Regjistrim huazimi/kthimi për çdo anëtar
- Menaxhim inventari: shto, ndrysho dhe fshi libra e DVD (artikujt me histori huazimesh s'fshihen, që raportet të mbeten të sakta)
- Import librash nga Open Library (kërkim sipas titullit)
- Krijim gjobe manuale për huazime aktive të vonuara
- Historia e pagesave dhe raporte
- Krijim llogarish për bibliotekarë të rinj

**Gjobat:** kthimi me vonesë krijon automatikisht gjobë prej 0.50 € në ditë, në të njëjtin transaksion me kthimin. Nëse bibliotekari e ka gjobitur huazimin më parë, ngarkohet vetëm diferenca.

**Në sfond:** një scheduler kontrollon çdo 30 sekonda rezervimet "gati për marrje" që s'janë marrë brenda 3 ditëve, i anulon dhe ia kalon artikullin anëtarit tjetër në radhë.

## Arkitektura

```
Main (CLI, menu sipas rolit)
  └── service/   LoanService, FineService, ReservationService, InventoryService, ReportService, AuthService
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

### 3. Të dhëna demo (opsionale)

```bash
psql -d biblioteka -f db/seed.sql
```

Shton 7 artikuj (5 libra, 2 DVD) dhe tre llogari me fjalëkalimin `demo1234`: **L001** (bibliotekar), **M001** dhe **M002** (anëtarë). Nëse e përdor këtë, hapi 4 s'nevojitet.

### 4. Llogaria e parë e bibliotekarit (pa seed)

Sign Up krijon vetëm anëtarë, dhe bibliotekarë të rinj mund të krijojë vetëm një bibliotekar ekzistues (menuja → "Shto bibliotekar të ri"). Prandaj i pari krijohet një herë direkt në DB; `pgcrypto` prodhon hash BCrypt që e pranon jBCrypt:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO persons (id, name, email, phone, person_type, password_hash)
VALUES ('L001', 'Emri Mbiemri', 'email@shembull.com', NULL, 'LIBRARIAN', crypt('fjalekalimi', gen_salt('bf', 10)));
INSERT INTO librarians (person_id, employee_code) VALUES ('L001', 'EMP001');
```

### 5. Ekzekutimi

```bash
mvn compile exec:java -Dexec.mainClass=org.example.Main
```

Ose nga IntelliJ: `Main.java` → Run. Fjalëkalimi fshihet me `****` vetëm në një terminal të vërtetë, sepse paneli Run i IntelliJ s'ofron `System.console()`.

## Testet

Testet e integrimit punojnë mbi një databazë të veçantë, që të mos prekin të dhënat reale:

```bash
createdb biblioteka_test
psql -d biblioteka_test -f db/schema.sql
mvn test -Ddb.url=jdbc:postgresql://localhost:5432/biblioteka_test
```

`-Ddb.url` (si dhe `-Ddb.user`, `-Ddb.password`) ka përparësi ndaj `config.properties`. Pa të, testet përdorin DB-në e konfiguruar.

| Testi | Lloji | Çfarë vërteton |
|---|---|---|
| `DomainModelTest` | njësie | afati 21-ditor i librit, ditët e vonesës, pagesa e gjobës s'e bën balancën negative |
| `LoanServiceAuthorizationTest` | integrimi | një anëtar s'mund të kthejë huazimin e një anëtari tjetër |
| `DBConnectionTest` | integrimi | çdo thirrje merr lidhje të veçantë: mbyllja e njërës s'prish tjetrën |
| `ReservationServiceTest` | integrimi | radha FIFO nga rezervimi te huazimi; s'lejohen rezervime të dyfishta, për artikuj të lirë ose me gjoba të papaguara |
| `OverdueFineTest` | integrimi | gjoba automatike në kthim të vonuar; gjoba manuale + automatike s'ngarkojnë dy herë |
| `InventoryServiceTest` | integrimi | ISBN e normalizuar dhe unike; redaktimi s'e bën "të lirë" një libër të huazuar; artikujt me histori s'fshihen |
| `AuthServiceTest` | integrimi | vetëm bibliotekarët krijojnë bibliotekarë; krijimi i llogarisë është atomik; validimi i Sign Up |

Testet s'kërkojnë asnjë të dhënë paraprake — kalojnë mbi një DB bosh të krijuar vetëm nga `schema.sql`. Çdo test krijon të dhënat e veta me prefiks `TEST_` (`TestData`) dhe i fshin në fund, edhe kur dështon.
