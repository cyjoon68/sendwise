import { resetMock, setClientId } from "@/shared/api";
import { DEFAULT_CLIENT_ID } from "@/shared/config";
import { cleanup } from "@testing-library/vue";
import { afterEach, beforeEach } from "vitest";

beforeEach(() => {
  sessionStorage.clear();
  resetMock();
  setClientId(DEFAULT_CLIENT_ID);
});

afterEach(() => {
  cleanup();
});
