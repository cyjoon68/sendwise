package com.sendwise.api.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendwise.api.application.CampaignService;
import com.sendwise.api.application.ExportService;
import com.sendwise.api.application.ResultService;
import com.sendwise.api.application.SenderService;
import com.sendwise.api.application.StatsService;
import com.sendwise.api.domain.ApiError;
import com.sendwise.api.infra.persistence.TemplateEntity;
import com.sendwise.api.infra.persistence.TemplateRepository;
import com.sendwise.api.infra.rules.RuleTables;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class V1Controller {
    private final RuleTables rules;
    private final CampaignService campaigns;
    private final ResultService results;
    private final StatsService stats;
    private final ExportService exports;
    private final SenderService senders;
    private final TemplateRepository templates;
    private final ObjectMapper mapper;

    public V1Controller(
            RuleTables rules,
            CampaignService campaigns,
            ResultService results,
            StatsService stats,
            ExportService exports,
            SenderService senders,
            TemplateRepository templates,
            ObjectMapper mapper
    ) {
        this.rules = rules;
        this.campaigns = campaigns;
        this.results = results;
        this.stats = stats;
        this.exports = exports;
        this.senders = senders;
        this.templates = templates;
        this.mapper = mapper;
    }

    @GetMapping("/v1/rules")
    public JsonNode rules() {
        return rules.rulesJson();
    }

    @PostMapping("/v1/campaigns")
    public ResponseEntity<Map<String, Object>> create(@RequestBody CampaignService.CreateCampaign body) {
        return ResponseEntity.status(201).body(campaigns.create(body));
    }

    @GetMapping("/v1/campaigns/{id}")
    public Map<String, Object> campaign(@PathVariable long id) {
        return campaigns.get(id);
    }

    @GetMapping("/v1/campaigns/{id}/messages")
    public Map<String, Object> messages(
            @PathVariable long id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return campaigns.messages(id, status, page, size);
    }

    @PostMapping("/v1/campaigns/{id}/exports")
    public ResponseEntity<Map<String, Object>> export(@PathVariable long id) {
        return ResponseEntity.status(202).body(exports.start(id));
    }

    @GetMapping("/v1/exports/{id}")
    public Map<String, Object> exportStatus(@PathVariable long id) {
        return exports.get(id);
    }

    @GetMapping("/v1/exports/{id}/file")
    public ResponseEntity<FileSystemResource> exportFile(@PathVariable long id) {
        FileSystemResource resource = new FileSystemResource(exports.file(id));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"campaign-results.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @GetMapping("/v1/stats")
    public Map<String, Object> stats(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam String groupBy
    ) {
        return stats.stats(from, to, groupBy);
    }

    @PostMapping("/carrier/{carrier}/results")
    public Map<String, Object> carrierResult(@PathVariable String carrier, @RequestBody CarrierResult body) {
        String code = body.carrierCode() != null && !body.carrierCode().isBlank() ? body.carrierCode() : body.code();
        long messageId;
        try {
            messageId = Long.parseLong(body.messageId());
        } catch (RuntimeException exception) {
            throw new ApiError(400, "MESSAGE_ID_REQUIRED", "메시지 번호가 필요해요");
        }
        return results.apply(carrier, messageId, code, body.commonCode());
    }

    @GetMapping("/v1/sender-numbers")
    public List<Map<String, Object>> senders(@RequestParam long clientId) {
        return senders.list(clientId);
    }

    @PostMapping("/v1/sender-numbers")
    public ResponseEntity<Map<String, Object>> createSender(@RequestBody SenderBody body) {
        if (body.clientId() == null) {
            throw new ApiError(400, "CLIENT_REQUIRED", "고객사 번호가 필요해요");
        }
        return ResponseEntity.status(201).body(senders.register(body.clientId(), body.phone()));
    }

    @GetMapping("/v1/sender-numbers/{id}")
    public Map<String, Object> sender(@PathVariable long id) {
        return senders.get(id);
    }

    @PatchMapping("/v1/sender-numbers/{id}")
    public Map<String, Object> patchSender(@PathVariable long id, @RequestBody SenderPatch body) {
        return senders.transition(id, body.status(), body.rejectReason());
    }

    @DeleteMapping("/v1/sender-numbers/{id}")
    public ResponseEntity<Void> deleteSender(@PathVariable long id) {
        senders.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/alimtalk-templates")
    public List<Map<String, Object>> templates(@RequestParam long clientId) {
        return templates.findByClientIdAndStatusOrderByIdAsc(clientId, "APPROVED").stream().map(this::templateView).toList();
    }

    private Map<String, Object> templateView(TemplateEntity entity) {
        JsonNode variables;
        try {
            variables = mapper.readTree(entity.getVariablesJson());
        } catch (Exception exception) {
            variables = mapper.createArrayNode();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", entity.getId());
        body.put("clientId", entity.getClientId());
        body.put("templateCode", entity.getTemplateCode());
        body.put("body", entity.getBody());
        body.put("variables", variables);
        body.put("status", entity.getStatus());
        return body;
    }

    public record CarrierResult(
            String messageId,
            String carrier,
            String recipient,
            String channel,
            String carrierCode,
            String commonCode,
            String code
    ) {
    }

    public record SenderBody(Long clientId, String phone) {
    }

    public record SenderPatch(String status, String rejectReason) {
    }
}
