export const normalizePhone = (raw: string) => {
  let compact = raw.trim().replace(/[\s-]/g, "");
  if (compact.startsWith("+82")) compact = `0${compact.slice(3)}`;
  const valid = /^010\d{8}$/.test(compact);
  return { normalized: valid ? compact : null, valid };
};
