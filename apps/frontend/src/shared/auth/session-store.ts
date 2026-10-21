import { DEFAULT_CLIENT_ID, SESSION_STORAGE_KEY } from "@/shared/config";
import { defineStore } from "pinia";
import { ref } from "vue";

export type Session = {
  clientId: number;
  balance: number | null;
};

export const useSessionStore = defineStore("session", () => {
  const clientId = ref(DEFAULT_CLIENT_ID);
  const balance = ref<number | null>(null);

  const apply = (session: Session) => {
    clientId.value = session.clientId;
    balance.value = session.balance;
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  };

  const hydrate = () => {
    const raw = sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) return;
    try {
      const session = JSON.parse(raw) as { clientId?: unknown; balance?: unknown };
      clientId.value = typeof session.clientId === "number" ? session.clientId : DEFAULT_CLIENT_ID;
      balance.value = typeof session.balance === "number" ? session.balance : null;
    } catch {
      clientId.value = DEFAULT_CLIENT_ID;
      balance.value = null;
    }
  };

  return { clientId, balance, apply, hydrate };
});
