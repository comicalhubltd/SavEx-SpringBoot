-- Financial corrections are new postings, never edits to prior entries.
CREATE TRIGGER journal_no_update BEFORE UPDATE ON journal_postings
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Journal postings are immutable';
CREATE TRIGGER journal_no_delete BEFORE DELETE ON journal_postings
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Journal postings are immutable';
CREATE TRIGGER customer_review_no_update BEFORE UPDATE ON customer_reviews
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Customer review evidence is immutable';
CREATE TRIGGER customer_review_no_delete BEFORE DELETE ON customer_reviews
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Customer review evidence is immutable';
