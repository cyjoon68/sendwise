import { parseAndClassify } from "@/shared/upload";
import { fireEvent, render, screen } from "@testing-library/vue";
import { describe, expect, it } from "vitest";
import RecipientUpload from "./recipient-upload.vue";

const csv = [
  "전화번호,이름",
  "01012345678,김하늘",
  "010-1234-5678,김하늘",
  "01099990000,박서준",
  "+82 10 5555 6666,이도윤",
  "abc,오류",
  "01000000000,거부",
].join("\n");

describe("recipient upload", () => {
  it("shows the four recipient groups", async () => {
    render(RecipientUpload, {
      props: {
        optOuts: ["01000000000"],
        parseFile: async (data: ArrayBuffer, optOuts: string[]) => parseAndClassify(
          new TextDecoder().decode(data),
          optOuts,
        ),
      },
    });
    const file = new File([csv], "recipients.csv", { type: "text/csv" });
    const input = screen.getByLabelText("수신자 파일");
    Object.defineProperty(input, "files", { value: [file] });
    await fireEvent.change(input);
    expect((await screen.findByTestId("group-valid")).textContent).toContain("3건");
    expect(screen.getByTestId("group-invalid").textContent).toContain("1건");
    expect(screen.getByTestId("group-duplicate").textContent).toContain("1건");
    expect(screen.getByTestId("group-opt-out").textContent).toContain("1건");
    expect(screen.getByText("01055556666")).toBeTruthy();
  });
});
