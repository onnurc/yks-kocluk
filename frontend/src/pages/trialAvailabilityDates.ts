export function rollingTrialDays(now = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Istanbul", year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(now);
  const value = (type: string) => Number(parts.find((part) => part.type === type)?.value);
  const base = new Date(Date.UTC(value("year"), value("month") - 1, value("day")));
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(base); date.setUTCDate(base.getUTCDate() + index); return date;
  });
}
