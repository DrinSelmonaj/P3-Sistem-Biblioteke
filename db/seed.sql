-- Te dhena demo per nje instalim te ri (pas db/schema.sql)
-- psql -d biblioteka -f db/seed.sql
--
-- Llogarite demo — fjalekalimi per te treja: demo1234
--   L001  bibliotekar  (menaxhon inventarin, gjobat, raportet)
--   M001  anetar
--   M002  anetar
-- Hash-et jane BCrypt (jBCrypt), te njejtin format qe prodhon AuthService.
-- ON CONFLICT DO NOTHING: skripti mund te ekzekutohet perseri pa gabime.

INSERT INTO persons (id, name, email, phone, person_type, password_hash) VALUES
    ('L001', 'Arta Bibliotekarja', 'arta@biblioteka.local', '044111222', 'LIBRARIAN',
     '$2a$10$kDkdnroD2LyKvazv9OupsukhfPZOvK5SiQcrIvfIHjXmg0w7XpCwm'),
    ('M001', 'Blerim Anetari', 'blerim@shembull.local', '044333444', 'MEMBER',
     '$2a$10$rBmDHOojEDrh5yUjYHciseieho2m9ICloCqFEdIp2KrT2XEpKMucK'),
    ('M002', 'Drita Anetarja', 'drita@shembull.local', NULL, 'MEMBER',
     '$2a$10$2wjzT.j7iZFOWydazS3bJOLDyvkjK3ajRDWmFe7utPtNhPEjAWi0C')
ON CONFLICT (id) DO NOTHING;

INSERT INTO librarians (person_id, employee_code) VALUES ('L001', 'EMP001') ON CONFLICT DO NOTHING;
INSERT INTO members (person_id) VALUES ('M001'), ('M002') ON CONFLICT DO NOTHING;

-- Katalogu: 5 libra + 2 DVD, nga kategori te ndryshme (emrat sipas enum Category)
INSERT INTO library_items (id, title, category, available, item_type) VALUES
    ('B001', 'Clean Code',                         'NON_FICTION', true, 'BOOK'),
    ('B002', 'The Pragmatic Programmer',           'SCIENCE',     true, 'BOOK'),
    ('B003', 'Introduction to Algorithms',         'SCIENCE',     true, 'BOOK'),
    ('B004', 'Kronikë në gur',                     'FICTION',     true, 'BOOK'),
    ('B005', 'Steve Jobs',                         'BIOGRAPHY',   true, 'BOOK'),
    ('D001', 'Cosmos: A Spacetime Odyssey',        'SCIENCE',     true, 'DVD'),
    ('D002', 'The Imitation Game',                 'HISTORY',     true, 'DVD')
ON CONFLICT (id) DO NOTHING;

INSERT INTO books (item_id, author, isbn) VALUES
    ('B001', 'Robert C. Martin',              '9780132350884'),
    ('B002', 'Andrew Hunt, David Thomas',     '9780135957059'),
    ('B003', 'Thomas H. Cormen',              '9780262046305'),
    ('B004', 'Ismail Kadare',                 '9781611453607'),
    ('B005', 'Walter Isaacson',               '9781451648539')
ON CONFLICT DO NOTHING;

INSERT INTO dvds (item_id, duration_minutes) VALUES
    ('D001', 559),
    ('D002', 114)
ON CONFLICT DO NOTHING;
