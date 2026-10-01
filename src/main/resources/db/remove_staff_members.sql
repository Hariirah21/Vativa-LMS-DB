-- Role Management now uses registered users plus the roles table.
-- Run once against an existing database after deploying the refactored backend.
DROP TABLE IF EXISTS staff_members;
