-- Adaptive-streaming (HLS) copy of a seek's video, set once it has been packaged:
-- /api/v1/media/hls/<video uuid>/master.m3u8. Null = only the plain video file exists.
ALTER TABLE reels ADD COLUMN IF NOT EXISTS hls_url VARCHAR(512);
