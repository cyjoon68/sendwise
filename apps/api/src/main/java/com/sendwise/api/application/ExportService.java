package com.sendwise.api.application;

import com.sendwise.api.domain.ApiError;
import com.sendwise.api.domain.rules.CodeCopy;
import com.sendwise.api.infra.persistence.CampaignRepository;
import com.sendwise.api.infra.persistence.ExportJobEntity;
import com.sendwise.api.infra.persistence.ExportJobRepository;
import com.sendwise.api.infra.persistence.MessageEntity;
import com.sendwise.api.infra.persistence.MessageRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ExportService {
    private static final String[] HEADERS = {
            "번호", "수신번호", "통신사", "채널", "상태", "통신사코드", "공통코드", "설명", "다음 조치", "대체발송 원본"
    };

    private final CampaignRepository campaigns;
    private final MessageRepository messages;
    private final ExportJobRepository jobs;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Path dir;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public ExportService(
            CampaignRepository campaigns,
            MessageRepository messages,
            ExportJobRepository jobs,
            PlatformTransactionManager manager,
            Clock clock,
            @Value("${sendwise.export-dir:build/exports}") String exportDir
    ) {
        this.campaigns = campaigns;
        this.messages = messages;
        this.jobs = jobs;
        this.tx = new TransactionTemplate(manager);
        this.clock = clock;
        this.dir = Path.of(exportDir);
    }

    @Transactional
    public Map<String, Object> start(long campaignId) {
        if (!campaigns.existsById(campaignId)) {
            throw new ApiError(404, "CAMPAIGN_NOT_FOUND", "캠페인을 찾을 수 없어요");
        }
        ExportJobEntity job = new ExportJobEntity();
        job.setCampaignId(campaignId);
        job.setStatus("PENDING");
        job.setCreatedAt(clock.instant());
        jobs.save(job);
        long id = job.getId();
        executor.execute(() -> run(id, campaignId));
        return view(job);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(long id) {
        return view(jobs.findById(id).orElseThrow(() -> new ApiError(404, "EXPORT_NOT_FOUND", "내려받기 작업을 찾을 수 없어요")));
    }

    @Transactional(readOnly = true)
    public Path file(long id) {
        ExportJobEntity job = jobs.findById(id)
                .orElseThrow(() -> new ApiError(404, "EXPORT_NOT_FOUND", "내려받기 작업을 찾을 수 없어요"));
        if (!"DONE".equals(job.getStatus()) || job.getFilePath() == null) {
            throw new ApiError(409, "EXPORT_NOT_READY", "엑셀 파일을 아직 만들고 있어요");
        }
        return Path.of(job.getFilePath());
    }

    private void run(long id, long campaignId) {
        mark(id, "RUNNING", null, null);
        Path path = dir.resolve(id + ".xlsx");
        try {
            Files.createDirectories(dir);
            try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
                workbook.setCompressTempFiles(true);
                Sheet sheet = workbook.createSheet("results");
                Row header = sheet.createRow(0);
                for (int i = 0; i < HEADERS.length; i++) {
                    header.createCell(i).setCellValue(HEADERS[i]);
                }
                int excelRow = 1;
                int page = 0;
                while (true) {
                    int current = page;
                    List<MessageEntity> batch = tx.execute(status -> messages
                            .findByCampaign_Id(campaignId, PageRequest.of(current, 500, Sort.by("id")))
                            .getContent());
                    if (batch == null || batch.isEmpty()) {
                        break;
                    }
                    for (MessageEntity message : batch) {
                        CodeCopy copy = CodeCopy.of(message.getCommonCode());
                        Row row = sheet.createRow(excelRow++);
                        row.createCell(0).setCellValue(message.getId());
                        row.createCell(1).setCellValue(message.getRecipient());
                        row.createCell(2).setCellValue(message.getCarrier());
                        row.createCell(3).setCellValue(message.getChannel());
                        row.createCell(4).setCellValue(message.getStatus());
                        row.createCell(5).setCellValue(nullToEmpty(message.getCarrierCode()));
                        row.createCell(6).setCellValue(nullToEmpty(message.getCommonCode()));
                        row.createCell(7).setCellValue(message.getCommonCode() == null ? "" : copy.explanation());
                        row.createCell(8).setCellValue(message.getCommonCode() == null ? "" : copy.actionHint());
                        if (message.getFallbackOfId() != null) {
                            row.createCell(9).setCellValue(message.getFallbackOfId());
                        }
                    }
                    if (batch.size() < 500) {
                        break;
                    }
                    page++;
                }
                try (OutputStream out = Files.newOutputStream(path)) {
                    workbook.write(out);
                }
                workbook.dispose();
            }
            mark(id, "DONE", path.toAbsolutePath().toString(), null);
        } catch (Exception exception) {
            mark(id, "FAILED", null, exception.getMessage());
        }
    }

    private void mark(long id, String status, String path, String error) {
        tx.executeWithoutResult(ignored -> {
            ExportJobEntity job = jobs.findById(id).orElseThrow();
            job.setStatus(status);
            job.setFilePath(path);
            if (error != null) {
                job.setErrorMessage(error.length() > 500 ? error.substring(0, 500) : error);
            }
            if ("DONE".equals(status) || "FAILED".equals(status)) {
                job.setFinishedAt(Instant.now());
            }
        });
    }

    private Map<String, Object> view(ExportJobEntity job) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", job.getId());
        body.put("campaignId", job.getCampaignId());
        body.put("status", job.getStatus());
        body.put("downloadUrl", "DONE".equals(job.getStatus()) ? "/v1/exports/" + job.getId() + "/file" : null);
        body.put("error", job.getErrorMessage());
        return body;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
