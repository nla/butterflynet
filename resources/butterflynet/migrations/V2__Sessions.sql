CREATE TABLE user (
  id INTEGER PRIMARY KEY,
  username VARCHAR(256) NOT NULL,
  issuer VARCHAR(256) NOT NULL,
  subject VARCHAR(256) NOT NULL,
  name TEXT NOT NULL,
  email TEXT NOT NULL,
  UNIQUE KEY issuer_subject_unique (issuer, subject)
);

CREATE TABLE session (
  id VARCHAR(32) PRIMARY KEY,
  user_id INTEGER,

  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);