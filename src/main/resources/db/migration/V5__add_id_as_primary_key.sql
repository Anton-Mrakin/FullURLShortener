-- Remove current primary key (short_code)
ALTER TABLE urls DROP CONSTRAINT urls_pkey;

-- Add id column as BIGINT with IDENTITY
ALTER TABLE urls ADD COLUMN id BIGINT GENERATED ALWAYS AS IDENTITY;

-- Set id as the new primary key
ALTER TABLE urls ADD PRIMARY KEY (id);
