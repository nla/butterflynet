CREATE TABLE user (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    issuer VARCHAR(100) NOT NULL,
    subject VARCHAR(100) NOT NULL,
    name TEXT NOT NULL,
    email TEXT NOT NULL,
    UNIQUE issuer_subject_unique (issuer, subject)
);

CREATE TABLE capture (
    id BIGINT NOT NULL AUTO_INCREMENT,
    state INTEGER NOT NULL DEFAULT 0,
    url TEXT,
    started TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status INTEGER,
    reason TEXT,
    size BIGINT,
    user_id BIGINT NOT NULL,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES user(id)
);

CREATE TABLE session (
    id VARCHAR(32) PRIMARY KEY,
    user_id INT NOT NULL,
    expiry BIGINT NOT NULL,

    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

CREATE INDEX session_expiry ON session (expiry);
