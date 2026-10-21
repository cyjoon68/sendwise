package com.sendwise.api.application;

import com.sendwise.api.domain.ApiError;
import com.sendwise.api.domain.rules.PhoneNumbers;
import com.sendwise.api.infra.balance.JdbcAccounts;
import com.sendwise.api.infra.persistence.SenderNumberEntity;
import com.sendwise.api.infra.persistence.SenderNumberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SenderService {
    private final SenderNumberRepository senders;
    private final JdbcAccounts accounts;
    private final Clock clock;

    public SenderService(SenderNumberRepository senders, JdbcAccounts accounts, Clock clock) {
        this.senders = senders;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(long clientId) {
        return senders.findByClientIdOrderByIdDesc(clientId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(long id) {
        return view(senders.findById(id).orElseThrow(() -> new ApiError(404, "SENDER_NOT_FOUND", "발신번호를 찾을 수 없어요")));
    }

    @Transactional
    public Map<String, Object> register(long clientId, String rawPhone) {
        if (!accounts.clientExists(clientId)) {
            throw new ApiError(404, "CLIENT_NOT_FOUND", "고객사를 찾을 수 없어요");
        }
        String phone = PhoneNumbers.compact(rawPhone);
        if (!PhoneNumbers.sender(phone)) {
            throw new ApiError(400, "INVALID_SENDER", "발신번호 형식을 확인해 주세요");
        }
        if (senders.findByClientIdAndPhone(clientId, phone).isPresent()) {
            throw new ApiError(409, "DUPLICATE_SENDER", "이미 등록된 번호예요");
        }
        SenderNumberEntity entity = new SenderNumberEntity();
        entity.setClientId(clientId);
        entity.setPhone(phone);
        entity.setStatus("SUBMITTED");
        entity.setCreatedAt(clock.instant());
        try {
            return view(senders.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiError(409, "DUPLICATE_SENDER", "이미 등록된 번호예요");
        }
    }

    @Transactional
    public Map<String, Object> transition(long id, String status, String rejectReason) {
        SenderNumberEntity entity = senders.findById(id)
                .orElseThrow(() -> new ApiError(404, "SENDER_NOT_FOUND", "발신번호를 찾을 수 없어요"));
        boolean allowed = switch (entity.getStatus() + "->" + status) {
            case "SUBMITTED->REVIEWING", "REVIEWING->APPROVED", "REVIEWING->REJECTED" -> true;
            default -> false;
        };
        if (!allowed) {
            throw new ApiError(409, "INVALID_STATUS", "이 상태로는 바꿀 수 없어요");
        }
        if ("REJECTED".equals(status) && (rejectReason == null || rejectReason.isBlank())) {
            throw new ApiError(400, "REJECT_REASON_REQUIRED", "반려 사유를 입력해 주세요");
        }
        entity.setStatus(status);
        entity.setRejectReason("REJECTED".equals(status) ? rejectReason : null);
        return view(entity);
    }

    @Transactional
    public void delete(long id) {
        if (!senders.existsById(id)) {
            throw new ApiError(404, "SENDER_NOT_FOUND", "발신번호를 찾을 수 없어요");
        }
        senders.deleteById(id);
    }

    private Map<String, Object> view(SenderNumberEntity entity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", entity.getId());
        body.put("clientId", entity.getClientId());
        body.put("phone", entity.getPhone());
        body.put("status", entity.getStatus());
        body.put("rejectReason", entity.getRejectReason());
        return body;
    }
}
