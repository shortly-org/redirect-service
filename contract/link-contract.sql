-- The links read model dashboard-service publishes: the view link_snapshot, readable by members of
-- role link_reader. Only the view's columns are the contract; the links table is a stand-in.
--
-- Change feed: SELECT ... FROM link_snapshot WHERE change_seq > :last ORDER BY change_seq LIMIT :n.
-- A row becomes visible up to 2 s after its updated_at, so each poll also re-reads the rows with
-- updated_at newer than the previous poll's start minus 2 s and a clock margin; the highest version
-- of a link wins.

CREATE SEQUENCE link_change_seq;

CREATE TABLE links (
    code        VARCHAR(9)     NOT NULL PRIMARY KEY,
    target_url  VARCHAR(2048)  NOT NULL,
    status      VARCHAR(16)    NOT NULL,
    version     INT            NOT NULL,
    change_seq  BIGINT         NOT NULL DEFAULT nextval('link_change_seq'),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE VIEW link_snapshot AS SELECT code, target_url, status, version, change_seq, updated_at FROM links;
