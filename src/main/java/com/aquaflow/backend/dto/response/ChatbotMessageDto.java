package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotMessageDto {
    private Long id;
    private String sessionId;
    private String sender;
    private String messageText;
    private OffsetDateTime timestamp;
    private String structuredPayloadJson;
    private String clientMessageId;
}
