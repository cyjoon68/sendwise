UPDATE alimtalk_template
SET status = 'REJECTED'
WHERE client_id = 1 AND template_code = 'WELCOME';

INSERT INTO alimtalk_template (client_id, template_code, body, variables_json, status)
VALUES
    (1, 'ORDER_SHIPPED', '#{name}님, 주문 #{orderNo}가 출고되었습니다.', '["name","orderNo"]', 'APPROVED'),
    (1, 'AUTH_CODE', '인증번호는 #{code}입니다.', '["code"]', 'APPROVED');
