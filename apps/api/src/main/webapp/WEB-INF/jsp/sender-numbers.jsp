<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>발신번호</title>
    <style>
        body { font-family: sans-serif; margin: 32px; color: #222; }
        table { border-collapse: collapse; width: 100%; max-width: 720px; }
        th, td { border-bottom: 1px solid #ddd; text-align: left; padding: 8px; }
        form.inline { display: inline; }
        .error { color: #b42318; }
        input[type=text] { padding: 6px; }
        button { padding: 6px 10px; }
    </style>
</head>
<body>
<h1>발신번호</h1>
<c:if test="${not empty error}">
    <p class="error">${error}</p>
</c:if>
<form method="post" action="/sender-numbers">
    <input type="hidden" name="clientId" value="${clientId}">
    <label>번호 <input type="text" name="phone" required placeholder="0212345678"></label>
    <button type="submit">번호 등록</button>
</form>
<table>
    <thead>
    <tr>
        <th>번호</th>
        <th>상태</th>
        <th>반려 사유</th>
        <th></th>
    </tr>
    </thead>
    <tbody>
    <c:forEach var="item" items="${numbers}">
        <tr>
            <td>${item.phone}</td>
            <td>
                <c:choose>
                    <c:when test="${item.status == 'SUBMITTED'}">서류 제출</c:when>
                    <c:when test="${item.status == 'REVIEWING'}">심사 중</c:when>
                    <c:when test="${item.status == 'APPROVED'}">승인</c:when>
                    <c:when test="${item.status == 'REJECTED'}">반려</c:when>
                    <c:otherwise>${item.status}</c:otherwise>
                </c:choose>
            </td>
            <td>${item.rejectReason}</td>
            <td>
                <c:if test="${item.status == 'SUBMITTED'}">
                    <form class="inline" method="post" action="/sender-numbers/${item.id}/review">
                        <input type="hidden" name="clientId" value="${clientId}">
                        <button type="submit">심사 시작</button>
                    </form>
                </c:if>
                <c:if test="${item.status == 'REVIEWING'}">
                    <form class="inline" method="post" action="/sender-numbers/${item.id}/approve">
                        <input type="hidden" name="clientId" value="${clientId}">
                        <button type="submit">승인</button>
                    </form>
                    <form class="inline" method="post" action="/sender-numbers/${item.id}/reject">
                        <input type="hidden" name="clientId" value="${clientId}">
                        <input type="text" name="rejectReason" placeholder="반려 사유" required>
                        <button type="submit">반려</button>
                    </form>
                </c:if>
            </td>
        </tr>
    </c:forEach>
    <c:if test="${empty numbers}">
        <tr><td colspan="4">등록된 번호가 없어요</td></tr>
    </c:if>
    </tbody>
</table>
</body>
</html>
