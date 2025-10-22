CREATE TABLE Artist (
  artist_id INT PRIMARY KEY,
  full_name VARCHAR(100),
  first_name VARCHAR(50),
  middle_names VARCHAR(50),
  last_name VARCHAR(50),
  nationality VARCHAR(50),
  style VARCHAR(50),
  birth DATE,
  death DATE
);
CREATE TABLE Artwork (
  work_id INT PRIMARY KEY,
  name VARCHAR(100),
  artist_id INT,
  style VARCHAR(50),
  museum_id INT,
  FOREIGN KEY (artist_id) REFERENCES Artist(artist_id),
  FOREIGN KEY (museum_id) REFERENCES Museum(museum_id)
);
CREATE TABLE Museum (
  museum_id INT PRIMARY KEY,
  name VARCHAR(100),
  address VARCHAR(150),
  phone VARCHAR(20),
  date DATE,
  country VARCHAR(50),
  city VARCHAR(50)
);
CREATE TABLE Museum_Hours (
  museum_id INT,
  day VARCHAR(10),
  open TIME,
  close TIME,
  PRIMARY KEY (museum_id, day),
  FOREIGN KEY (museum_id) REFERENCES Museum(museum_id)
);
CREATE TABLE CanvasSize (
  size_id INT PRIMARY KEY,
  width DECIMAL(5,2),
  height DECIMAL(5,2),
  label VARCHAR(50)
);
CREATE TABLE ProductSize (
  work_id INT,
  size_id INT,
  sale_price DECIMAL(10,2),
  regular_price DECIMAL(10,2),
  PRIMARY KEY (work_id, size_id),
  FOREIGN KEY (work_id) REFERENCES Artwork(work_id),
  FOREIGN KEY (size_id) REFERENCES CanvasSize(size_id)
);
CREATE TABLE Image (
  image_id INT PRIMARY KEY AUTO_INCREMENT,
  work_id INT,
  url VARCHAR(255),
  thumbnail_small_url VARCHAR(255),
  thumbnail_large_url VARCHAR(255),
  FOREIGN KEY (work_id) REFERENCES Artwork(work_id)
);
CREATE TABLE Subject (
  subject_id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100)
);

