import { execSync } from "node:child_process";
import { existsSync, readdirSync, writeFileSync } from "node:fs";
import os from "node:os";
import { join } from "node:path";
import puppeteer from "puppeteer-core";

const url = process.env.MEASURE_URL ?? "http://127.0.0.1:5173/measure?auto=1";

const chromePath = () => {
  const candidates = [];
  if (process.env.CHROME_PATH) candidates.push(process.env.CHROME_PATH);
  candidates.push("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
  const cache = join(process.env.HOME ?? "", ".cache/puppeteer/chrome");
  if (existsSync(cache)) {
    for (const entry of readdirSync(cache)) {
      for (const arch of ["chrome-mac-arm64", "chrome-mac-x64"]) {
        candidates.push(join(
          cache,
          entry,
          arch,
          "Google Chrome for Testing.app",
          "Contents",
          "MacOS",
          "Google Chrome for Testing",
        ));
      }
    }
  }
  return candidates.find((path) => existsSync(path));
};

const chrome = chromePath();
if (!chrome) throw new Error("Chrome was not found");

const browser = await puppeteer.launch({
  executablePath: chrome,
  headless: true,
  args: ["--disable-dev-shm-usage"],
});

try {
  const page = await browser.newPage();
  page.setDefaultTimeout(180_000);
  await page.goto(url, { waitUntil: "domcontentloaded" });
  await page.waitForFunction(() => {
    const node = document.querySelector("#measure-result");
    const status = node?.getAttribute("data-status");
    return status === "done" || status === "mismatch";
  });
  const report = await page.$eval("#measure-result", (node) => ({
    status: node.getAttribute("data-status"),
    body: node.textContent ?? "",
  }));
  const output = {
    status: report.status,
    measuredAt: new Date().toISOString(),
    command: "node scripts/measure-upload.mjs",
    url,
    os: `${os.type()} ${os.release()}`,
    node: process.version,
    chrome: execSync(`"${chrome}" --version`, { encoding: "utf8" }).trim(),
    result: JSON.parse(report.body),
  };
  const path = "/tmp/sendwise-measure-result.json";
  writeFileSync(path, `${JSON.stringify(output, null, 2)}\n`);
  process.stdout.write(`${JSON.stringify(output, null, 2)}\n`);
  if (output.status !== "done") process.exitCode = 2;
} finally {
  await browser.close();
}
