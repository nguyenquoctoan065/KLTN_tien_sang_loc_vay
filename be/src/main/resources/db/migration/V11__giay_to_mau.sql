-- AppDocument.qualityStatus thêm SPECIMEN: máy đọc (Gemini) báo ảnh rõ nhưng là giấy tờ mẫu/specimen (xem app.ocr.allow-specimen).
ALTER TABLE application_document DROP CONSTRAINT ck_application_document_quality;
ALTER TABLE application_document
    ADD CONSTRAINT ck_application_document_quality CHECK (quality_status IN ('OK','BLURRY','WRONG_FORMAT','TOO_LARGE','WRONG_TYPE','CORRUPTED','SPECIMEN'));