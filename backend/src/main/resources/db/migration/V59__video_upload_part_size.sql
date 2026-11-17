-- Sessions issued before adaptive part sizing always used 8 MiB chunks.
ALTER TABLE training_video_upload ADD COLUMN part_size INT NOT NULL DEFAULT 8388608;
