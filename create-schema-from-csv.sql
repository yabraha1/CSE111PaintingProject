BEGIN TRANSACTION;


DROP TABLE IF EXISTS work_subject;
DROP TABLE IF EXISTS subject;
DROP TABLE IF EXISTS image_link;
DROP TABLE IF EXISTS product_size;
DROP TABLE IF EXISTS canvas_size;
DROP TABLE IF EXISTS museum_hours;
DROP TABLE IF EXISTS museum;
DROP TABLE IF EXISTS work;
DROP TABLE IF EXISTS artist;

CREATE TABLE artist (
  id INTEGER PRIMARY KEY,                
  full_name TEXT,
  first_name TEXT,
  middle_names TEXT,
  last_name TEXT,
  nationality TEXT,
  style TEXT,
  birth TEXT,     
  death TEXT
);
CREATE TABLE work (
  id INTEGER PRIMARY KEY,                 
  title TEXT,
  artist_id INTEGER REFERENCES artist(id) ON DELETE SET NULL,
  style TEXT,
  museum_id INTEGER REFERENCES museum(id) ON DELETE SET NULL,
  year INTEGER,
  medium TEXT,
  inventory_number TEXT,
  description TEXT
);
CREATE TABLE museum (
  id INTEGER PRIMARY KEY,                 
  name TEXT NOT NULL,
  address TEXT,
  city TEXT,
  state TEXT,
  postal_code TEXT,
  country TEXT,
  phone TEXT,
  website TEXT
);
CREATE TABLE museum_hours (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  museum_id INTEGER NOT NULL REFERENCES museum(id) ON DELETE CASCADE,
  day TEXT NOT NULL,
  open_time TEXT,
  close_time TEXT,
  notes TEXT
);


CREATE TABLE canvas_size (
  id INTEGER PRIMARY KEY,
  width REAL,
  height REAL,
  label TEXT,
  unit TEXT DEFAULT 'cm'
);


CREATE TABLE product_size (
  work_id INTEGER NOT NULL REFERENCES work(id) ON DELETE CASCADE,
  size_id INTEGER NOT NULL REFERENCES canvas_size(id) ON DELETE SET NULL,
  sale_price REAL,
  regular_price REAL,
  PRIMARY KEY (work_id, size_id)
);


CREATE TABLE image_link (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  work_id INTEGER REFERENCES work(id) ON DELETE CASCADE,
  url TEXT,
  thumbnail_small_url TEXT,
  thumbnail_large_url TEXT
);

CREATE TABLE subject (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE
);


CREATE TABLE work_subject (
  work_id INTEGER NOT NULL REFERENCES work(id) ON DELETE CASCADE,
  subject_id INTEGER NOT NULL REFERENCES subject(id) ON DELETE CASCADE,
  PRIMARY KEY (work_id, subject_id)
);

COMMIT;

