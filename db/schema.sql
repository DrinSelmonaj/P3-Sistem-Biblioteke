-- Kapitulli 2: Skema e databazes per P3 - Sistem Biblioteke
-- Strategji: Class Table Inheritance

CREATE TABLE library_items (
    id VARCHAR(50) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    available BOOLEAN NOT NULL DEFAULT true,
    item_type VARCHAR(10) NOT NULL CHECK (item_type IN ('BOOK', 'DVD'))
);

CREATE TABLE books (
    item_id VARCHAR(50) PRIMARY KEY REFERENCES library_items(id) ON DELETE CASCADE,
    author VARCHAR(255) NOT NULL,
    isbn VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE dvds (
    item_id VARCHAR(50) PRIMARY KEY REFERENCES library_items(id) ON DELETE CASCADE,
    duration_minutes INT NOT NULL
);


CREATE TABLE persons (
                      id VARCHAR(50) PRIMARY KEY,
                      name VARCHAR(255) NOT NULL,
                      email VARCHAR(255) NOT NULL,
                      phone VARCHAR(20),
                      person_type VARCHAR(10) NOT NULL CHECK (person_type IN ('MEMBER', 'LIBRARIAN')),
                      password_hash VARCHAR(60) NOT NULL
             );

CREATE TABLE members (
    person_id VARCHAR(50) PRIMARY KEY REFERENCES persons(id) ON DELETE CASCADE,
    unpaid_fines NUMERIC(10,2) NOT NULL DEFAULT 0.00
);

CREATE TABLE librarians (
    person_id VARCHAR(50) PRIMARY KEY REFERENCES persons(id) ON DELETE CASCADE,
    employee_code VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE loans (
    id SERIAL PRIMARY KEY,
    member_id VARCHAR(50) NOT NULL REFERENCES members(person_id),
    item_id VARCHAR(50) NOT NULL REFERENCES library_items(id),
    borrow_date DATE NOT NULL,
    due_date DATE NOT NULL,
    return_date DATE
);

CREATE TABLE reservations (
    id SERIAL PRIMARY KEY,
    member_id VARCHAR(50) NOT NULL REFERENCES members(person_id),
    item_id VARCHAR(50) NOT NULL REFERENCES library_items(id),
    reservation_date DATE NOT NULL,
    fulfilled BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE fines (
                       id SERIAL PRIMARY KEY,
                       loan_id INT NOT NULL REFERENCES loans(id),
                       amount NUMERIC(10,2) NOT NULL,
                       issued_date DATE NOT NULL,
                       paid BOOLEAN NOT NULL DEFAULT false,
                       paid_date TIMESTAMP
);
