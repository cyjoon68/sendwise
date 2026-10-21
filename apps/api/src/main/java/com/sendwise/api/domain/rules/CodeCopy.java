package com.sendwise.api.domain.rules;

public record CodeCopy(String explanation, String actionHint) {
    public static CodeCopy of(String common) {
        return switch (common == null ? "" : common) {
            case "SUCCESS" -> new CodeCopy("상대가 메시지를 받았어요", "따로 할 일은 없어요");
            case "INVALID_NUMBER" -> new CodeCopy("없는 번호라 전달하지 못했어요", "번호를 확인한 뒤 다시 등록해 주세요");
            case "OPTED_OUT" -> new CodeCopy("수신을 거부한 번호예요", "수신거부 목록을 확인해 주세요");
            case "DEVICE_OFF" -> new CodeCopy("휴대폰이 꺼져 있어 전달하지 못했어요", "잠시 후 다시 보내 주세요");
            case "SPAM_BLOCKED" -> new CodeCopy("스팸으로 차단됐어요", "문구를 바꾼 뒤 다시 보내 주세요");
            default -> new CodeCopy("다른 이유로 전달하지 못했어요", "같은 오류가 반복되면 문의해 주세요");
        };
    }
}
