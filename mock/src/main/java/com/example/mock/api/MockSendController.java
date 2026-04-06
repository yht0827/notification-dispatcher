package com.example.mock.api;

import com.example.mock.service.MockSendService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock")
public class MockSendController {

    private final MockSendService mockSendService;

    public MockSendController(MockSendService mockSendService) {
        this.mockSendService = mockSendService;
    }

    @PostMapping("/send")
    public MockSendSuccessResponse send(@Valid @RequestBody MockSendRequest request) {
        return mockSendService.handleSend(request);
    }
}
