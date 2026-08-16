ALTER TABLE attendee_profiles ADD COLUMN email VARCHAR(320);
CREATE INDEX idx_attendee_profiles_email ON attendee_profiles (email);
