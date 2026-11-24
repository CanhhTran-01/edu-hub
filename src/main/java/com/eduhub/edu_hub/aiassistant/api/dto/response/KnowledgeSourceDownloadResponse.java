package com.eduhub.edu_hub.aiassistant.api.dto.response;

/** Tệp gốc chỉ được trả qua endpoint tải xuống, không ghép vào JSON danh sách hoặc chi tiết nguồn. */
public record KnowledgeSourceDownloadResponse(String filename, String contentType, byte[] fileBytes) {}
