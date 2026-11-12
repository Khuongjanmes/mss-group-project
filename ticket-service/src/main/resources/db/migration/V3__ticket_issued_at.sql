-- Thời điểm phát hành để "Vé của tôi" xếp mới nhất trước.
ALTER TABLE tickets ADD COLUMN issued_at TIMESTAMPTZ NOT NULL DEFAULT now();
