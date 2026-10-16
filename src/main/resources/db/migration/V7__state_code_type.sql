-- Align the state code with the entity's VARCHAR mapping.
ALTER TABLE states MODIFY code VARCHAR(3) NOT NULL;
