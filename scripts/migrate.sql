SET client_encoding = 'UTF8';

-- Information from the Attribution Metadata page.
CREATE TABLE "attributions" (
  "pageid"      integer  NOT NULL,
  "created_by"  text     NOT NULL,
  "kind"        text     NOT NULL,
  PRIMARY KEY ("created_by", "pageid")
);

-- Non-mandatory statement overrides; analogous to the "queries" table.
CREATE TABLE "statements" (
  "name"      character varying(100)  PRIMARY KEY,
  "statement" character varying(1000)
);

-- Configures how frequently background processes run.
CREATE TABLE "timers" (
  "name"     character varying(100)  PRIMARY KEY,
  "minutes"  int  NOT NULL
);

INSERT INTO "timers" ("name", "minutes") VALUES
    ('attributions', 60),
    ('bans', 60),
    ('pages', 15),
    ('staff', 60),
    ('titles', 5);

-- The current implementation of rolling:
--   a) can crash the server
--   b) stores an immense amount of information that is essentially inaccessible
DROP TABLE "roll";
DROP TABLE "rolls";

CREATE TABLE "rolls" (
  "rollid"    serial  PRIMARY KEY,
  "username"  character varying(50) NOT NULL,
  "amount"    integer  NOT NULL,
  "type"      "char"  NOT NULL,
  "size"      integer  NOT NULL,
  "total"     integer  NOT NULL
);
CREATE INDEX ON "rolls" ("username");

CREATE TABLE "roll" (
  "rollid"  integer  NOT NULL  references "rolls"("rollid")  ON DELETE CASCADE,
  "value"   integer  NOT NULL
);
CREATE INDEX ON "roll" ("rollid");


-- Upgrading foreign constraints

ALTER TABLE ONLY "pages" ADD CONSTRAINT "pageid_unique" UNIQUE ("pageid");
ALTER TABLE ONLY "tags" ADD CONSTRAINT "tags_pkey" PRIMARY KEY ("tagid");
ALTER TABLE ONLY "tags" ADD CONSTRAINT "tag_unique" UNIQUE ("tag");

ALTER TABLE ONLY "captains" DROP CONSTRAINT "captains_team_id_fkey";
ALTER TABLE ONLY "captains" ADD CONSTRAINT "captains_team_id_fkey"
    FOREIGN KEY ("team_id")  REFERENCES "teams"("team_id")  ON DELETE CASCADE;

ALTER TABLE ONLY "nicks" DROP CONSTRAINT "nicks_id_fkey";
ALTER TABLE ONLY "nicks" ADD CONSTRAINT "nicks_id_fkey"
    FOREIGN KEY ("id")  REFERENCES "nick_groups"("id")  ON DELETE CASCADE;

ALTER TABLE ONLY "team_members" DROP CONSTRAINT "team_members_team_id_fkey";
ALTER TABLE ONLY "team_members" ADD CONSTRAINT "team_members_team_id_fkey"
    FOREIGN KEY ("team_id")  REFERENCES "teams"("team_id")  ON DELETE CASCADE;

DELETE FROM "pagetags" where "pageid" not in (select "pageid" from "pages");
ALTER TABLE ONLY "pagetags" ADD CONSTRAINT "pagetags_pageid_fkey"
    FOREIGN KEY ("pageid")  REFERENCES "pages"("pageid")  ON DELETE CASCADE;
DELETE FROM "pagetags" where "tagid" not in (select "tagid" from "tags");
ALTER TABLE ONLY "pagetags" ADD CONSTRAINT "pagetags_tagid_fkey"
    FOREIGN KEY ("tagid")  REFERENCES "tags"("tagid")  ON DELETE CASCADE;
ALTER TABLE ONLY "pagetags" ADD COLUMN "updatetime"  timestamp;
UPDATE "pagetags" SET "updatetime" = current_timestamp;
ALTER TABLE ONLY "pagetags" ALTER COLUMN "updatetime" SET NOT NULL;

ALTER TABLE ONLY "captains" DROP CONSTRAINT "captains_staff_id_fkey";
ALTER TABLE ONLY "team_members" DROP CONSTRAINT "team_members_staff_id_fkey";

DELETE FROM "staff" WHERE "wikidotid" is null OR "level" is null
    OR "activity_level" is null OR "displayname" is null;

ALTER TABLE "staff" ALTER COLUMN "wikidotid" SET NOT NULL;
ALTER TABLE "staff" ALTER COLUMN "level" SET NOT NULL;
ALTER TABLE "staff" ALTER COLUMN "activity_level" SET NOT NULL;
ALTER TABLE "staff" ALTER COLUMN "displayname" SET NOT NULL;


ALTER TABLE ONLY "staff" DROP CONSTRAINT "staff_pkey";
ALTER TABLE ONLY "staff" DROP COLUMN "staff_id";
ALTER TABLE ONLY "staff" RENAME "wikidotid" TO "staff_id";
ALTER TABLE ONLY "staff" ADD CONSTRAINT "staff_pkey" PRIMARY KEY ("staff_id");
ALTER TABLE ONLY "staff" ADD COLUMN "updatetime"  timestamp;
UPDATE "staff" SET "updatetime" = current_timestamp;
ALTER TABLE ONLY "staff" ALTER COLUMN "updatetime" SET NOT NULL;

ALTER TABLE ONLY "captains" ADD CONSTRAINT "captains_staff_id_fkey"
    FOREIGN KEY ("staff_id")  REFERENCES "staff"("staff_id")  ON DELETE CASCADE;

ALTER TABLE ONLY "team_members" ADD CONSTRAINT "team_members_staff_id_fkey"
    FOREIGN KEY ("staff_id")  REFERENCES "staff"("staff_id")  ON DELETE CASCADE;


-- Adding in NOT NULL constraints to just about everything.
UPDATE "channeltoggles" SET "enabled" = false WHERE "enabled" is null;
ALTER TABLE "channeltoggles" ALTER COLUMN "enabled" SET NOT NULL;

ALTER TABLE "hostmasks" ALTER COLUMN "established" SET DEFAULT current_date;
UPDATE "hostmasks" SET "established" = current_date WHERE "established" is null;
ALTER TABLE "hostmasks" ALTER COLUMN "established" SET NOT NULL;

DELETE FROM "hugs" WHERE "hug" is null;
ALTER TABLE "hugs" ALTER COLUMN "hug" SET NOT NULL;

UPDATE "pages" SET "updatetime" = current_date WHERE "updatetime" is null;
ALTER TABLE "pages" ALTER COLUMN "updatetime" SET NOT NULL;

ALTER TABLE "pages" ALTER COLUMN "rating" SET DEFAULT 0;
UPDATE "pages" SET "rating" = 0 WHERE "rating" is null;
ALTER TABLE "pages" ALTER COLUMN "rating" SET NOT NULL;

UPDATE "pages" SET "created_on" = current_timestamp WHERE "created_on" is null;
ALTER TABLE "pages" ALTER COLUMN "created_on" SET NOT NULL;

ALTER TABLE "pages" DROP COLUMN "scppage";


DELETE FROM "pronouns" WHERE "username" is null;
ALTER TABLE "pronouns" ALTER COLUMN "username" SET NOT NULL;

UPDATE "pronouns" SET "accepted" = false WHERE "accepted" is null;
ALTER TABLE "pronouns" ALTER COLUMN "accepted" SET NOT NULL;


DELETE FROM "properties" WHERE "key" is null OR "value" is null;
ALTER TABLE "properties" ALTER COLUMN "key" SET NOT NULL;
ALTER TABLE "properties" ALTER COLUMN "value" SET NOT NULL;

ALTER TABLE "properties" ALTER COLUMN "updated" SET DEFAULT current_date;
UPDATE "properties" SET "updated" = current_date WHERE "updated" is null;
ALTER TABLE "properties" ALTER COLUMN "updated" SET NOT NULL;

ALTER TABLE "properties" ALTER COLUMN "public" SET DEFAULT false;
UPDATE "properties" SET "public" = false WHERE "public" is null;
ALTER TABLE "properties" ALTER COLUMN "public" SET NOT NULL;


ALTER TABLE "tags" DROP COLUMN "lastupdated";
DELETE FROM "tags" WHERE "tag" is null;
ALTER TABLE "tags" ALTER COLUMN "tag" SET NOT NULL;


DELETE FROM "teams" WHERE "team_name" is null;
ALTER TABLE "teams" ALTER COLUMN "team_name" SET NOT NULL;
ALTER TABLE ONLY "teams" ADD CONSTRAINT "team_name_unique" UNIQUE ("team_name");

DELETE FROM "tells" WHERE "username" is null OR "message" is null OR "sender" is null;
ALTER TABLE "tells" ALTER COLUMN "username" SET NOT NULL;
ALTER TABLE "tells" ALTER COLUMN "message" SET NOT NULL;
ALTER TABLE "tells" ALTER COLUMN "sender" SET NOT NULL;

ALTER TABLE "tells" ALTER COLUMN "tell_time" SET DEFAULT current_timestamp;
UPDATE "tells" SET "tell_time" = current_timestamp WHERE "tell_time" is null;
ALTER TABLE "tells" ALTER COLUMN "tell_time" SET NOT NULL;

UPDATE "tells" SET "delivered" = false WHERE "delivered" is null;
ALTER TABLE "tells" ALTER COLUMN "delivered" SET NOT NULL;


DELETE FROM "users" WHERE "last_message" is null OR "first_message" is null;
ALTER TABLE ONLY "users" ALTER COLUMN "last_message" SET NOT NULL;
ALTER TABLE ONLY "users" ALTER COLUMN "first_message" SET NOT NULL;

ALTER TABLE "users" ALTER COLUMN "first_seen" SET DEFAULT current_timestamp;
UPDATE "users" SET "first_seen" = current_timestamp WHERE "first_seen" is null;
ALTER TABLE "users" ALTER COLUMN "first_seen" SET NOT NULL;

ALTER TABLE "users" ALTER COLUMN "last_seen" SET DEFAULT current_timestamp;
UPDATE "users" SET "last_seen" = current_timestamp WHERE "last_seen" is null;
ALTER TABLE "users" ALTER COLUMN "last_seen" SET NOT NULL;


DELETE FROM "captains" WHERE "team_id" is null OR "staff_id" is null;
ALTER TABLE "captains" ALTER COLUMN "team_id" SET NOT NULL;
ALTER TABLE "captains" ALTER COLUMN "staff_id" SET NOT NULL;


DELETE FROM "nicks" WHERE "id" is null OR "nick" is null;
ALTER TABLE ONLY "nicks" ALTER COLUMN "id" SET NOT NULL;
ALTER TABLE ONLY "nicks" ALTER COLUMN "nick" SET NOT NULL;


DELETE FROM "pronoun" WHERE "pronounid" is null OR "pronoun" is null;
ALTER TABLE "pronoun" ALTER COLUMN "pronounid" SET NOT NULL;
ALTER TABLE "pronoun" ALTER COLUMN "pronoun" SET NOT NULL;


DELETE FROM "team_members" WHERE "team_id" is null OR "staff_id" is null;
ALTER TABLE ONLY "team_members" ALTER COLUMN "team_id" SET NOT NULL;
ALTER TABLE ONLY "team_members" ALTER COLUMN "staff_id" SET NOT NULL;


-- Primary keys

ALTER TABLE ONLY "nicks" ADD CONSTRAINT "nicks_pk" PRIMARY KEY ("id", "nick");

ALTER TABLE ONLY "properties" DROP CONSTRAINT "key_value";
ALTER TABLE ONLY "properties" ADD CONSTRAINT "properties_pk" PRIMARY KEY ("key", "value");

ALTER TABLE ONLY "team_members" ADD CONSTRAINT "team_members_pk" PRIMARY KEY ("team_id", "staff_id");

ALTER TABLE ONLY "users" DROP CONSTRAINT "user_unique";
ALTER TABLE ONLY "users" ADD CONSTRAINT "users_pk" PRIMARY KEY ("username", "channel");

-- Join indices

CREATE INDEX ON "pagetags" ("tagid");
CREATE INDEX ON "pages" ("created_by");
CREATE INDEX ON "pronoun" ("pronounid");
CREATE INDEX ON "teams" ("captain_id");
CREATE INDEX ON "tells" ("username");
CREATE INDEX ON "team_members" ("staff_id");
