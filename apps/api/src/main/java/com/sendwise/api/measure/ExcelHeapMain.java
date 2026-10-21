package com.sendwise.api.measure;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ExcelHeapMain {
    private static final int ROWS = 1_000_000;

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "sxssf" : args[0];
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long[] peak = {memory.getHeapMemoryUsage().getUsed()};
        Thread sampler = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                long used = memory.getHeapMemoryUsage().getUsed();
                if (used > peak[0]) {
                    peak[0] = used;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        sampler.setDaemon(true);
        sampler.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("shutdownPeakHeapBytes=" + peak[0])));
        System.out.println("mode=" + mode);
        System.out.println("dataRows=" + ROWS);
        System.out.println("java=" + Runtime.version());
        System.out.println("maxHeapBytes=" + Runtime.getRuntime().maxMemory());
        Path file = Files.createTempFile("sendwise-" + mode + "-", ".xlsx");
        long start = System.nanoTime();
        boolean completed = false;
        try {
            if ("xssf".equals(mode)) {
                writeXssf(file);
            } else {
                writeSxssf(file);
            }
            completed = true;
            System.out.println("fileBytes=" + Files.size(file));
        } catch (OutOfMemoryError error) {
            System.out.println("error=OutOfMemoryError");
        } finally {
            Files.deleteIfExists(file);
            sampler.interrupt();
            System.out.println("completed=" + completed);
            System.out.println("elapsedMillis=" + ((System.nanoTime() - start) / 1_000_000L));
            System.out.println("peakHeapBytes=" + peak[0]);
        }
    }

    private static void writeXssf(Path file) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            fill(workbook.createSheet("results"));
            try (var out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }
    }

    private static void writeSxssf(Path file) throws Exception {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            fill(workbook.createSheet("results"));
            try (var out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
            workbook.dispose();
        }
    }

    private static void fill(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("번호");
        header.createCell(1).setCellValue("수신번호");
        header.createCell(2).setCellValue("본문");
        for (int i = 1; i <= ROWS; i++) {
            Row row = sheet.createRow(i);
            row.createCell(0).setCellValue(i);
            row.createCell(1).setCellValue("01000000000");
            row.createCell(2).setCellValue("ping");
        }
    }

    private ExcelHeapMain() {
    }
}
