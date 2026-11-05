-- Existing users must set a password before they can log in.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
