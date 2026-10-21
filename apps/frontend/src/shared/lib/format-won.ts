export const formatWon = (value: number) => `${new Intl.NumberFormat("ko-KR").format(value)}원`;
