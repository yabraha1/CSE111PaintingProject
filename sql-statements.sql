--  script containing the SQL queries and data modification statements (INSERT/UPDATE/DELETE)
-- specified in your use-case diagram. There should be at least 20 SQL statements (2-student
-- teams) or 30 SQL statements (3-student teams) and they should display diversity, i.e., queries
-- with different format and modification statements of different type.

--1 show first 10 artists
SELECT id AS artist_id, full_name 
FROM artist 
ORDER BY id 
LIMIT 10;

--2 Count works per artist (top 10 busiest artists)
SELECT a.id AS artist_id, a.full_name, COUNT(w.id) AS works_count
FROM artist a
LEFT JOIN work w ON w.artist_id = a.id
GROUP BY a.id
ORDER BY works_count DESC
LIMIT 10;

--3 List works with their museum name 
SELECT w.id AS work_id, w.title, m.id AS museum_id, m.name AS museum_name
FROM work w
LEFT JOIN museum m ON w.museum_id = m.id
ORDER BY w.id
LIMIT 25;

--4 Number of works per museum (only museums that have at least one work)
SELECT m.id AS museum_id, m.name, COUNT(w.id) AS work_count
FROM museum m
JOIN work w ON w.museum_id = m.id
GROUP BY m.id
HAVING work_count > 0
ORDER BY work_count DESC
LIMIT 20;

--5 find all Baroque works and show artist name and museum
SELECT w.id, w.title, a.full_name AS artist, m.name AS museum
FROM work w
LEFT JOIN artist a ON w.artist_id = a.id
LEFT JOIN museum m ON w.museum_id = m.id
WHERE LOWER(COALESCE(w.style, '')) LIKE '%baroque%'
ORDER BY w.id
LIMIT 50;

--6 Subquery: work(s) with the greatest number of product size variants
SELECT w.id AS work_id, w.title, COUNT(ps.size_id) AS variant_count
FROM work w
LEFT JOIN product_size ps ON ps.work_id = w.id
GROUP BY w.id
HAVING variant_count = (
	SELECT MAX(t.cnt) FROM (
		SELECT work_id, COUNT(size_id) AS cnt FROM product_size GROUP BY work_id
	) t
)
ORDER BY work_id;

--7 Average sale price per canvas size label
SELECT cs.label, cs.id AS canvas_id, AVG(ps.sale_price) AS avg_sale_price, COUNT(*) AS price_points
FROM product_size ps
JOIN canvas_size cs ON ps.size_id = cs.id
WHERE ps.sale_price IS NOT NULL
GROUP BY cs.id
ORDER BY avg_sale_price DESC
LIMIT 30;


--8 top 10 subjects by number of works
WITH subj_counts AS (
	SELECT s.id, s.name, COUNT(ws.work_id) AS works
	FROM subject s
	LEFT JOIN work_subject ws ON ws.subject_id = s.id
	GROUP BY s.id
)
SELECT id, name, works FROM subj_counts ORDER BY works DESC LIMIT 10;

--9 artists who have works in more than 3 distinct museums
SELECT a.id AS artist_id, a.full_name,
	(SELECT COUNT(DISTINCT w2.museum_id) FROM work w2 WHERE w2.artist_id = a.id AND w2.museum_id IS NOT NULL) AS distinct_museums
FROM artist a
WHERE distinct_museums > 3
ORDER BY distinct_museums DESC;


--10 add a new subject if it doesn't exist (admin user)
INSERT OR IGNORE INTO subject(name) VALUES('Impressionism');

--11 remove a subject
DELETE FROM subject
WHERE LOWER(TRIM(name)) = LOWER(TRIM('Impressionism'));
COMMIT;

--12 Find works that have no product sizes defined
SELECT w.id, w.title FROM work w
LEFT JOIN product_size ps ON ps.work_id = w.id
WHERE ps.work_id IS NULL
ORDER BY w.id LIMIT 50;

--13 Find works with multiple different subjects (more than 1 subject)
SELECT w.id, w.title, COUNT(ws.subject_id) AS subject_count
FROM work w
JOIN work_subject ws ON ws.work_id = w.id
GROUP BY w.id
HAVING subject_count > 1
ORDER BY subject_count DESC
LIMIT 50;

--14 number of images per work 
SELECT w.id AS work_id, w.title, COUNT(il.id) AS image_count
FROM work w
LEFT JOIN image_link il ON il.work_id = w.id
GROUP BY w.id
ORDER BY image_count DESC
LIMIT 25;

--15 list top 20 works by total revenue potential
SELECT w.id AS work_id, w.title, SUM(COALESCE(ps.sale_price,0)) AS total_sale_price
FROM work w
JOIN product_size ps ON ps.work_id = w.id
GROUP BY w.id
ORDER BY total_sale_price DESC
LIMIT 20;


--16 Top 10 artists by average sale price of their works
SELECT a.id AS artist_id, a.full_name, AVG(ps.sale_price) AS avg_sale_price, COUNT(ps.work_id) AS price_points
FROM artist a
JOIN work w ON w.artist_id = a.id
JOIN product_size ps ON ps.work_id = w.id
WHERE ps.sale_price IS NOT NULL
GROUP BY a.id
HAVING price_points >= 3
ORDER BY avg_sale_price DESC
LIMIT 10;

--17 Oldest artists by birth year
SELECT id AS artist_id, full_name, birth
FROM artist
WHERE birth IS NOT NULL AND TRIM(birth)<>''
ORDER BY CAST(birth AS INTEGER) ASC
LIMIT 10;

--18 Price range per work (min and max sale_price)
SELECT w.id AS work_id, w.title, MIN(ps.sale_price) AS min_sale, MAX(ps.sale_price) AS max_sale, COUNT(ps.size_id) AS variants
FROM work w
LEFT JOIN product_size ps ON ps.work_id = w.id
GROUP BY w.id
ORDER BY (MAX(ps.sale_price) - MIN(ps.sale_price)) DESC
LIMIT 50;

--19 List first 50 works that are tagged with subject = 'Portraits'
SELECT w.id AS work_id, w.title, s.name AS subject
FROM work w
JOIN work_subject ws ON ws.work_id = w.id
JOIN subject s ON s.id = ws.subject_id
WHERE LOWER(TRIM(s.name)) = 'portraits'
ORDER BY w.id
LIMIT 50;

-- 20 Subjects per museum
SELECT s.name AS subject, m.name AS museum, COUNT(DISTINCT w.id) AS works_count
FROM subject s
JOIN work_subject ws ON ws.subject_id = s.id
JOIN work w ON w.id = ws.work_id
JOIN museum m ON m.id = w.museum_id
WHERE m.id IS NOT NULL
GROUP BY s.id, m.id
ORDER BY works_count DESC
LIMIT 50;

-- 21 Canvas usage by artist 
SELECT a.id AS artist_id, a.full_name AS artist, cs.id AS canvas_id, cs.label, COUNT(*) AS usage_count
FROM artist a
JOIN work w ON w.artist_id = a.id
JOIN product_size ps ON ps.work_id = w.id
JOIN canvas_size cs ON cs.id = ps.size_id
GROUP BY a.id, cs.id
ORDER BY usage_count DESC
LIMIT 100;

-- 22 Top artists per museum by average sale price 
SELECT m.id AS museum_id, m.name AS museum, a.id AS artist_id, a.full_name AS artist,
	   AVG(ps.sale_price) AS avg_sale, COUNT(DISTINCT w.id) AS works_count
FROM museum m
JOIN work w ON w.museum_id = m.id
JOIN artist a ON a.id = w.artist_id
JOIN product_size ps ON ps.work_id = w.id
WHERE ps.sale_price IS NOT NULL
GROUP BY m.id, a.id
ORDER BY m.id, avg_sale DESC
LIMIT 100;

-- 23 Works in museums that are open on Sunday (work -> museum -> museum_hours)
SELECT m.id AS museum_id, m.name AS museum, COUNT(w.id) AS works_on_museum_opening
FROM work w
JOIN museum m ON m.id = w.museum_id
JOIN museum_hours mh ON mh.museum_id = m.id
WHERE LOWER(TRIM(mh.day)) = 'sunday'
GROUP BY m.id
ORDER BY works_on_museum_opening DESC
LIMIT 50;

-- 24 Update Value in all rows of a column
-- Fix typo in museum_hours.day: change 'Thusday' -> 'Thursday'

BEGIN TRANSACTION;
UPDATE museum_hours
SET day = 'Thursday'
WHERE LOWER(TRIM(day)) = 'thusday';
COMMIT;

-- Verify
SELECT id, museum_id, day, open_time, close_time FROM museum_hours WHERE LOWER(TRIM(day)) = 'thursday' ORDER BY museum_id LIMIT 20;

