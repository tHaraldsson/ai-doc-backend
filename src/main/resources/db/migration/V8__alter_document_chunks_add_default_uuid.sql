ALTER TABLE document_chunks
DROP CONSTRAINT IF EXISTS document_chunks_pkey;

ALTER TABLE document_chunks
DROP CONSTRAINT IF EXISTS document_chunks_pkey1;

ALTER TABLE document_chunks
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

ALTER TABLE document_chunks
    ADD PRIMARY KEY (id);