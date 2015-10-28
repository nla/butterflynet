CREATE TABLE user (
  id INT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  issuer VARCHAR(256) NOT NULL,
  subject VARCHAR(256) NOT NULL,
  name TEXT NOT NULL,
  email TEXT NOT NULL,
  UNIQUE KEY issuer_subject_unique (issuer, subject)
);

CREATE TABLE session (
  id VARCHAR(32) PRIMARY KEY,
  user_id INT NOT NULL,
  expiry BIGINT NOT NULL,

  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

CREATE INDEX session_expiry ON session (expiry);