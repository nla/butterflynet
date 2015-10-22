CREATE TABLE user (
  username VARCHAR(64) NOT NULL PRIMARY KEY,
  issuer VARCHAR(256) NOT NULL,
  subject VARCHAR(256) NOT NULL,
  name TEXT NOT NULL,
  email TEXT NOT NULL,
  UNIQUE KEY issuer_subject_unique (issuer, subject)
);

CREATE TABLE session (
  id VARCHAR(32) PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  expiry BIGINT NOT NULL,

  FOREIGN KEY (username) REFERENCES user(username) ON DELETE CASCADE
);

CREATE INDEX session_expiry ON session (expiry);