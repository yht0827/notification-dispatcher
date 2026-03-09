package com.example.mock.api;

import com.example.mock.service.MockSendService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/email/send")
    public ResponseEntity<MockSendSuccessResponse> sendEmail(@Valid @RequestBody MockSendRequest request) {
        return ResponseEntity.ok(mockSendService.handleSend(request));
    }

    @PostMapping("/sms/send")
    public ResponseEntity<MockSendSuccessResponse> sendSms(@Valid @RequestBody MockSendRequest request) {
        return ResponseEntity.ok(mockSendService.handleSend(request));
    }

    @PostMapping("/kakao/send")
    public ResponseEntity<MockSendSuccessResponse> sendKakao(@Valid @RequestBody MockSendRequest request) {
        return ResponseEntity.ok(mockSendService.handleSend(request));
    }
}
