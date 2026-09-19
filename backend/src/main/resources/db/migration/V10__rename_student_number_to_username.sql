-- The registered identifier is a username/student ID, not necessarily a numeric
-- institutional number. Keep existing data while making that meaning explicit.
ALTER TABLE students RENAME COLUMN student_number TO student_username;
ALTER INDEX students_student_number_key RENAME TO students_student_username_key;
ALTER TABLE registration_attempts RENAME COLUMN student_number_hash TO student_username_hash;
