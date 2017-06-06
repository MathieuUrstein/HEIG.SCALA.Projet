# --- !Ups

CREATE TABLE "user"(
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  fullname VARCHAR(100) NOT NULL,
  email VARCHAR(60) NOT NULL,
  password VARCHAR(60) NOT NULL,
  currency VARCHAR(20) NOT NULL,
  CONSTRAINT "unique_email" UNIQUE(email)
);

CREATE TABLE "budget"(
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  userId INTEGER NOT NULL,
  name VARCHAR(60) NOT NULL,
  type VARCHAR(60) NOT NULL,
  used DOUBLE NOT NULL,
  "left" DOUBLE NOT NULL,
  exceeding DOUBLE NOT NULL,
  persistent INTEGER NOT NULL,
  reported BOOLEAN NOT NULL,
  color VARCHAR(60) NOT NULL,
  FOREIGN KEY(userId) REFERENCES "user"(id)
);

CREATE TABLE "takes_from"(
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  budgetGoesToId INTEGER NOT NULL,
  budgetTakesFromId INTEGER NOT NULL,
  "order" INTEGER NOT NULL,
  FOREIGN KEY(budgetGoesToId) REFERENCES "budget"(id),
  FOREIGN KEY(budgetTakesFromId) REFERENCES "budget"(id),
  CONSTRAINT "unique_row" UNIQUE(budgetGoesToId, budgetTakesFromId)
);

CREATE TABLE "transaction"(
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  userId INTEGER NOT NULL,
  name VARCHAR(60) NOT NULL,
  date DATE NOT NULL,
  amount DOUBLE NOT NULL,
  FOREIGN KEY(userId) REFERENCES "user"(id)
);

CREATE TABLE "exchange"(
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  userId INTEGER NOT NULL,
  name VARCHAR(60) NOT NULL,
  date DATE NOT NULL,
  type VARCHAR(60) NOT NULL,
  amount DOUBLE NOT NULL,
  FOREIGN KEY(userId) REFERENCES "user"(id)
);

# --- !Downs

DROP TABLE "user";
DROP TABLE "budget";
DROP TABLE "takes_from";
DROP TABLE "transaction";
DROP TABLE "exchange";
