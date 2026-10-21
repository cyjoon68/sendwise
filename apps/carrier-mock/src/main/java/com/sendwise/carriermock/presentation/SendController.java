package com.sendwise.carriermock.presentation;

import com.sendwise.carriermock.domain.AcceptSend;
import com.sendwise.carriermock.domain.Receipt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SendController {

    private final AcceptSend acceptSend;

    public SendController(AcceptSend acceptSend) {
        this.acceptSend = acceptSend;
    }

    @PostMapping("/send")
    public ResponseEntity<Receipt> send(@RequestBody SendBody body) {
        Receipt receipt = acceptSend.accept(body.toCommand(), System.currentTimeMillis());
        if (receipt.accepted()) {
            return ResponseEntity.status(202).body(receipt);
        }
        int status = "TPS_EXCEEDED".equals(receipt.reason()) ? 429 : 400;
        return ResponseEntity.status(status).body(receipt);
    }
}
