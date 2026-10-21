import { routes } from "@/app/router";
import { setClientId } from "@/shared/api";
import { useSessionStore } from "@/shared/auth";
import { parseAndClassify } from "@/shared/upload";
import { fireEvent, render, screen } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import { describe, expect, it } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import ComposePage from "./compose-page.vue";

const mountPage = async (balance: number) => {
  const pinia = createPinia();
  setActivePinia(pinia);
  const session = useSessionStore();
  session.apply({ clientId: 1, balance });
  setClientId(1);
  const router = createRouter({ history: createMemoryHistory(), routes });
  render(ComposePage, {
    global: { plugins: [pinia, router] },
    props: {
      parseFile: async (data: ArrayBuffer, optOuts: string[]) => parseAndClassify(
        new TextDecoder().decode(data),
        optOuts,
      ),
    },
  });
  await screen.findByRole("option", { name: "0212345678" });
};

const typeBody = async (value: string) => {
  const textarea = screen.getByLabelText("본문");
  (textarea as HTMLTextAreaElement).value = value;
  await fireEvent.input(textarea);
};

describe("compose page", () => {
  it("highlights characters outside the charset and disables send", async () => {
    await mountPage(50_000);
    await typeBody("안녕😀");
    expect(document.querySelector(".outside")?.textContent).toBe("😀");
    expect((screen.getByTestId("send-button") as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText("깨지는 글자를 바꿔 주세요")).toBeTruthy();
  });

  it("shows the LMS price delta", async () => {
    await mountPage(50_000);
    await typeBody("가".repeat(46));
    expect(screen.getByText("LMS예요. SMS보다 건당 21원 더 들어요.")).toBeTruthy();
  });

  it("disables send when the balance is short", async () => {
    await mountPage(0);
    await typeBody("가");
    const sender = screen.getByLabelText("발신번호") as HTMLSelectElement;
    sender.value = "0212345678";
    await fireEvent.change(sender);
    const file = new File(["전화번호,이름\n01012345678,김하늘\n"], "recipients.csv", { type: "text/csv" });
    const input = screen.getByLabelText("수신자 파일");
    Object.defineProperty(input, "files", { value: [file] });
    await fireEvent.change(input);
    await screen.findByText("1건");
    expect(screen.getByText("9원 부족해요")).toBeTruthy();
    expect((screen.getByTestId("send-button") as HTMLButtonElement).disabled).toBe(true);
  });
});
