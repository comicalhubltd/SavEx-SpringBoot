-- Align the persisted invite code with the entity's VARCHAR mapping.
ALTER TABLE `groups` MODIFY invite_code VARCHAR(8) NOT NULL;
