package com.daomanliu.test10.controller;

import com.daomanliu.test10.common.Result;
import com.daomanliu.test10.service.WeatherToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder, WeatherToolService weatherToolService) {
        this.chatClient = builder
                .defaultSystem("你是一个友好的中文助手。用户问天气时，请调用天气工具获取实时数据后再回答，不要编造天气信息。")
                .defaultTools(weatherToolService)
                .build();
    }

    public record ChatMessage(String role, String content) {}

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody List<ChatMessage> messages) {
        List<Message> promptMessages = new ArrayList<>();
        for (ChatMessage m : messages) {
            if ("user".equals(m.role())) {
                promptMessages.add(new UserMessage(m.content()));
            } else {
                promptMessages.add(new AssistantMessage(m.content()));
            }
        }

        return chatClient.prompt()
                .messages(promptMessages)
                .stream()
                .content()
                .onErrorResume(e -> Flux.just("[[ERROR]]" + e.getMessage()));
    }
}