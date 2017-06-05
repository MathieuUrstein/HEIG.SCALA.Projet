# --- !Ups

CREATE TABLE "user"(
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  fullname VARCHAR(100) NOT NULL,
  email VARCHAR(60) NOT NULL,
  password VARCHAR(60) NOT NULL,
  currency VARCHAR(20) NOT NULL,
  CONSTRAINT "unique_email" UNIQUE(email)
);

CREATE TABLE "transaction"(
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  userId INTEGER NOT NULL,
  name VARCHAR(60) NOT NULL,
  date DATE NOT NULL,
  amount DOUBLE NOT NULL,
  FOREIGN KEY(userId) REFERENCES "user"(id)
);

# --- !Downs

DROP TABLE "user";
DROP TABLE "transaction";
