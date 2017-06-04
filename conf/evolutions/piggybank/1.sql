# --- !Ups

CREATE TABLE user (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  fullname VARCHAR(100) NOT NULL,
  email VARCHAR(60) NOT NULL,
  password VARCHAR(60) NOT NULL,
  currency VARCHAR(20) NOT NULL,
  CONSTRAINT unique_email UNIQUE (email)
);


# --- !Downs

DROP TABLE user;
