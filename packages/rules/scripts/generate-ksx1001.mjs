import { spawnSync } from "node:child_process";
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");

const pythonGrid = () => {
  const code = `
import sys
rows = []
for lead in range(0xA1, 0x100):
    for trail in range(0xA1, 0x100):
        raw = bytes([lead, trail])
        try:
            ch = raw.decode("euc_kr")
        except UnicodeDecodeError:
            continue
        if len(ch) != 1:
            sys.stderr.write("multi %02X%02X\\n" % (lead, trail))
            sys.exit(1)
        rows.append("%02X%02X\\t%04X" % (lead, trail, ord(ch)))
for i in range(0x80):
    ch = bytes([i]).decode("euc_kr")
    if ch != chr(i):
        sys.stderr.write("ascii %02X\\n" % i)
        sys.exit(1)
sys.stdout.write("\\n".join(rows))
`;
  const run = spawnSync("python3", ["-c", code], {
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
  });
  if (run.status !== 0) {
    process.stderr.write(run.stderr || "python euc_kr failed\n");
    process.exit(1);
  }
  const map = new Map();
  for (const line of run.stdout.split("\n")) {
    if (!line) continue;
    const [bytes, cp] = line.split("\t");
    map.set(bytes, cp);
  }
  const version = spawnSync("python3", ["-c", "import sys; print(sys.version.split()[0])"], {
    encoding: "utf8",
  });
  return { map, version: version.stdout.trim() };
};

const decodeNode = (lead, trail) => {
  try {
    const ch = new TextDecoder("euc-kr", { fatal: true }).decode(new Uint8Array([lead, trail]));
    if ([...ch].length !== 1) return null;
    return ch;
  } catch {
    return null;
  }
};

const nodeGrid = () => {
  const map = new Map();
  for (let lead = 0xa1; lead <= 0xfe; lead += 1) {
    for (let trail = 0xa1; trail <= 0xfe; trail += 1) {
      const ch = decodeNode(lead, trail);
      if (!ch) continue;
      const bytes = lead.toString(16).padStart(2, "0").toUpperCase() + trail.toString(16).padStart(2, "0").toUpperCase();
      map.set(bytes, ch.codePointAt(0).toString(16).toUpperCase().padStart(4, "0"));
    }
  }
  for (let i = 0; i < 0x80; i += 1) {
    const ch = new TextDecoder("euc-kr", { fatal: true }).decode(new Uint8Array([i]));
    if (ch !== String.fromCharCode(i)) {
      process.stderr.write(`node ascii mismatch ${i.toString(16)}\n`);
      process.exit(1);
    }
  }
  return map;
};

const hangulBytes = (bytes) => {
  const n = Number.parseInt(bytes, 16);
  return n >= 0xb0a1 && n <= 0xc8fe;
};

const py = pythonGrid();
const node = nodeGrid();
const keys = [...new Set([...py.map.keys(), ...node.keys()])].sort();
const hangul = [];
const nonHangul = [];
const codecDisagreements = [];

for (const bytes of keys) {
  const pyCp = py.map.get(bytes) ?? null;
  const nodeCp = node.get(bytes) ?? null;
  if (pyCp !== nodeCp) {
    codecDisagreements.push({
      bytes,
      python: pyCp ? `U+${pyCp}` : null,
      node: nodeCp ? `U+${nodeCp}` : null,
    });
    continue;
  }
  if (pyCp.length !== 4) {
    process.stderr.write(`non-bmp ${bytes} U+${pyCp}\n`);
    process.exit(1);
  }
  const ch = String.fromCharCode(Number.parseInt(pyCp, 16));
  if (hangulBytes(bytes)) hangul.push(ch);
  else nonHangul.push(ch);
}

const hangulText = hangul.join("");
const nonHangulText = nonHangul.join("");

if ([...hangulText].length !== 2350) {
  process.stderr.write(`hangul count ${[...hangulText].length}, expected 2350\n`);
  process.exit(1);
}
if (new Set(hangul).size !== 2350) {
  process.stderr.write("hangul not unique\n");
  process.exit(1);
}
if (hangulText.includes("똠") || hangulText.includes("햏")) {
  process.stderr.write("non-syllable hangul entered the 2350\n");
  process.exit(1);
}
if (!hangulText.startsWith("가각간") || !hangulText.endsWith("힝")) {
  process.stderr.write("hangul order mismatch\n");
  process.exit(1);
}
for (const ch of hangul) {
  const cp = ch.codePointAt(0);
  if (cp < 0xac00 || cp > 0xd7a3) {
    process.stderr.write(`not a hangul syllable U+${cp.toString(16)}\n`);
    process.exit(1);
  }
}
for (const ch of nonHangul) {
  const cp = ch.codePointAt(0);
  if (cp >= 0xac00 && cp <= 0xd7a3) {
    process.stderr.write(`syllable outside hangul rows U+${cp.toString(16)}\n`);
    process.exit(1);
  }
}

const table = {
  hangul: hangulText,
  nonHangul: nonHangulText,
  hangulCount: 2350,
  nonHangulCount: [...nonHangulText].length,
  hangulRange: {
    leadFrom: "B0",
    leadTo: "C8",
    trailFrom: "A1",
    trailTo: "FE",
    holes: 0,
  },
  ascii: { from: 0, to: 127, bytes: 1 },
  ksx1001Bytes: 2,
  codec: {
    rule: "Keep a character only when Python euc_kr and Node TextDecoder('euc-kr') map the same byte pair to it.",
    node: process.version,
    python: py.version,
  },
  codecDisagreements,
  codecDisagreementCount: codecDisagreements.length,
};

writeFileSync(join(root, "ksx1001.json"), `${JSON.stringify(table, null, 2)}\n`);
process.stdout.write(
  `hangul ${table.hangulCount} nonHangul ${table.nonHangulCount} disagreements ${table.codecDisagreementCount} node ${process.version} python ${py.version}\n`,
);
