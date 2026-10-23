-- AppDocument.qualityStatus thêm CORRUPTED: kho lưu tệp (Cloudinary "Invalid PDF file"…) từ chối tệp hỏng / không mở được.
ALTER TABLE application_document DROP CONSTRAINT ck_application_document_quality;
ALTER TABLE application_document
    ADD CONSTRAINT ck_application_document_quality CHECK (quality_status IN ('OK','BLURRY','WRONG_FORMAT','TOO_LARGE','WRONG_TYPE','CORRUPTED'));
