-- Luồng Advisor không lưu nhãn phân loại câu hỏi; bỏ cả CHECK đi kèm cột này.
-- Giữ nguyên ID, nội dung, vai trò, thời gian và mọi liên kết trích dẫn của hội thoại.
ALTER TABLE chat_messages DROP COLUMN intent;
