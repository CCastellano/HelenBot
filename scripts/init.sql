SET client_encoding = 'UTF8';

CREATE TABLE "attributions" (
  "pageid"      integer  NOT NULL,
  "created_by"  text     NOT NULL,
  "kind"        text     NOT NULL,
  PRIMARY KEY ("created_by", "pageid")
);

CREATE TABLE "channeltoggles" (
  "channel"  text  NOT NULL,
  "command"  text  NOT NULL,
  "enabled"  boolean  NOT NULL  DEFAULT false,
  PRIMARY KEY ("channel", "command")
);

CREATE TABLE "hostmasks" (
  "username"     character varying(100)  NOT NULL,
  "hostmask"     character varying(100)  NOT NULL,
  "established"  date  NOT NULL  DEFAULT current_date,
  PRIMARY KEY  ("username", "hostmask")
);

CREATE TABLE "hugs" (
  "username"  text  PRIMARY KEY,
  "hug"       text  NOT NULL
);

CREATE TABLE "nick_groups" (
  "id"  serial  PRIMARY KEY
);

CREATE TABLE "pages" (
  "pageid"      serial  PRIMARY KEY,
  "pagename"    character varying(100)  NOT NULL  UNIQUE,
  "updatetime"  timestamp  NOT NULL,
  "title"       text  NOT NULL,
  "rating"      integer  NOT NULL  DEFAULT 0,
  "created_by"  text,
  "created_on"  timestamp  NOT NULL,
  "scptitle"    text
);
CREATE INDEX ON "pages" ("pagename");
CREATE INDEX ON "pages" ("created_by")

CREATE TABLE "pronouns" (
  "pronounid"  serial  PRIMARY KEY,
  "username"   text  NOT NULL,
  "accepted"   boolean  NOT NULL  DEFAULT false
);

CREATE TABLE "properties" (
  "key"      character varying(50)  NOT NULL,
  "value"    text  NOT NULL,
  "updated"  date  NOT NULL  DEFAULT current_date,
  "public"   boolean  NOT NULL  DEFAULT false,
  PRIMARY KEY ("key", "value")
);

CREATE TABLE "rolls" (
  "rollid"    serial  PRIMARY KEY,
  "username"  character varying(50) NOT NULL,
  "amount"    integer  NOT NULL,
  "type"      "char"  NOT NULL,
  "size"      integer  NOT NULL,
  "total"     integer  NOT NULL
);
CREATE INDEX ON "rolls" ("username");

CREATE TABLE "staff" (
  "staff_id"         integer  PRIMARY KEY,
  "username"         text  NOT NULL  UNIQUE,
  "timezone"         text,
  "contact_methods"  text,
  "activity_level"   text  NOT NULL,
  "level"            text  NOT NULL,
  "displayname"      text  NOT NULL,
  "updatetime"  timestamp  NOT NULL
);

CREATE TABLE "statements" (
  "name"       character varying(100)  PRIMARY KEY,
  "statement"  character varying(1000)
);

CREATE TABLE "tags" (
  "tagid" serial  PRIMARY KEY,
  "tag"   text  NOT NULL  UNIQUE
);

CREATE TABLE "teams" (
  "team_id"     serial  PRIMARY KEY,
  "team_name"   text  NOT NULL  UNIQUE,
  "captain_id"  integer
);
CREATE INDEX ON "teams" ("captain_id");

CREATE TABLE "tells" (
  "username"        character varying(100)  NOT NULL,
  "tell_time"       timestamp  NOT NULL  DEFAULT current_timestamp,
  "message"         character varying(400)  NOT NULL,
  "sender"          character varying(50)  NOT NULL,
  "privatemessage"  boolean  NOT NULL  DEFAULT true
);
CREATE INDEX ON "tells" ("username");

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

CREATE TABLE "users" (
  "username"       text  NOT NULL,
  "first_seen"     date  NOT NULL  DEFAULT current_date,
  "last_seen"      timestamp  NOT NULL  default current_timestamp,
  "last_message"   text  NOT NULL,
  "first_message"  text  NOT NULL,
  "channel"        text  NOT NULL,
  PRIMARY KEY ("username", "channel")
);

CREATE TABLE "nicks" (
  "id"    integer  NOT NULL  references "nick_groups"("id")  ON DELETE CASCADE,
  "nick"  text  NOT NULL,
  PRIMARY KEY ("id", "nick")
);

CREATE TABLE "pagetags" (
  "pageid"  integer  NOT NULL  references "pages"("pageid")  ON DELETE CASCADE,
  "tagid"   integer  NOT NULL  references "tags"("tagid")  ON DELETE CASCADE,
  "updatetime"  timestamp  NOT NULL,
  PRIMARY KEY ("pageid", "tagid")
);
CREATE INDEX ON "pagetags" ("tagid");

CREATE TABLE "pronoun" (
  "pronounid"  integer  NOT NULL  references "pronouns"("pronounid")  ON DELETE CASCADE,
  "pronoun"    text  NOT NULL
);
CREATE INDEX ON "pronoun" ("pronounid");

CREATE TABLE "roll" (
  "rollid"  integer  NOT NULL  references "rolls"("rollid")  ON DELETE CASCADE,
  "value"   integer  NOT NULL
);
CREATE INDEX ON "roll" ("rollid");

CREATE TABLE "team_members" (
  "team_id"   integer  NOT NULL  references "teams"("team_id")  ON DELETE CASCADE,
  "staff_id"  integer  NOT NULL  references "staff"("staff_id")  ON DELETE CASCADE,
  PRIMARY KEY ("team_id", "staff_id")
);
CREATE INDEX ON "team_members" ("staff_id");
