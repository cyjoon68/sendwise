const variablePart = /^#\{([^}]+)\}$/;

export const matchTemplate = (template: string, text: string) => {
  const parts = template.split(/(#\{[^}]+\})/).filter((part) => part !== "");
  let rest = text;
  let consumed = 0;
  const variables: Record<string, string> = {};
  for (let index = 0; index < parts.length; index += 1) {
    const part = parts[index] ?? "";
    const variable = variablePart.exec(part);
    if (variable) {
      const name = variable[1] ?? "";
      const nextLiteral = parts.slice(index + 1).find((item) => !variablePart.test(item));
      if (nextLiteral === undefined) {
        variables[name] = rest;
        consumed += rest.length;
        rest = "";
        continue;
      }
      const found = rest.indexOf(nextLiteral);
      if (found < 0) return { ok: false as const, index: consumed };
      variables[name] = rest.slice(0, found);
      rest = rest.slice(found);
      consumed += found;
      continue;
    }
    if (!rest.startsWith(part)) return { ok: false as const, index: consumed };
    rest = rest.slice(part.length);
    consumed += part.length;
  }
  if (rest.length > 0) return { ok: false as const, index: consumed };
  return { ok: true as const, variables };
};

export const mismatchParts = (text: string, index: number) => {
  if (index <= 0) return [{ text, bad: true }];
  if (index >= text.length) return [{ text, bad: false }];
  return [
    { text: text.slice(0, index), bad: false },
    { text: text.slice(index), bad: true },
  ];
};
