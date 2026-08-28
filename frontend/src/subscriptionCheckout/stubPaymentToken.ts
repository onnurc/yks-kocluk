const STUB_TOKEN_PATTERN = /^stub-checkout-(\d+)-[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function paymentIdFromStubToken(token: string | undefined): number | null {
  const match = token?.match(STUB_TOKEN_PATTERN);
  if (!match) return null;
  const paymentId = Number(match[1]);
  return Number.isSafeInteger(paymentId) && paymentId > 0 ? paymentId : null;
}
