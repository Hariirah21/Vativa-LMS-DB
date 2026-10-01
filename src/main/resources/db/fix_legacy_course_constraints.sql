-- The current course model uses category_id, instructor_id, and course_level.
-- These legacy text columns may remain in an older Neon schema and must not
-- reject inserts when the current model leaves them empty.
BEGIN;

ALTER TABLE courses ALTER COLUMN category DROP NOT NULL;
ALTER TABLE courses ALTER COLUMN instructor DROP NOT NULL;
ALTER TABLE courses ALTER COLUMN level DROP NOT NULL;

COMMIT;
