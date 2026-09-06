package com.multichat.message.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {
    // REST history endpoints delegate to MessageService; submit commands arrive through WebSocket.
}
