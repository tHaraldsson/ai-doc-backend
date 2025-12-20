DELETE FROM documents d1
WHERE EXISTS (
    SELECT 1 FROM documents d2
    WHERE d2.user_id = d1.user_id
      AND d2.file_name = d1.file_name
      AND d2.id < d1.id
);

ALTER TABLE documents
    ADD CONSTRAINT unique_user_filename UNIQUE (user_id, file_name);