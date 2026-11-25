-- Nhãn mục không còn thuộc metadata chunk hoặc contract trích dẫn khi chia văn bản theo token.
-- Bỏ cả nhãn snapshot cũ; giữ nội dung, vector, ID, trang và các liên kết lịch sử hiện có.
ALTER TABLE knowledge_chunks DROP COLUMN section_title;
ALTER TABLE message_chunk_references DROP COLUMN section_title;
