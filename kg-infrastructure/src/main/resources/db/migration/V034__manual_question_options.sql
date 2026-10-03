-- Manual options survive imports and automatic backfill across restarts.
ALTER TABLE questions ADD COLUMN options_manual BOOLEAN NOT NULL DEFAULT FALSE;
