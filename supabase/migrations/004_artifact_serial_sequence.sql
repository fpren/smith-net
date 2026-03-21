-- Persistent sequence for Summary Artifact serial numbers
-- Replaces in-memory counter that reset on server restart
CREATE SEQUENCE IF NOT EXISTS artifact_serial_seq START 1;
