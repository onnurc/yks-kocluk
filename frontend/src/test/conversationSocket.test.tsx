import { render, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useConversationSocket } from "../messaging/useConversationSocket";

const mocks = vi.hoisted(() => ({ clients: [] as Array<{ activate: ReturnType<typeof vi.fn>; deactivate: ReturnType<typeof vi.fn>; subscribe: ReturnType<typeof vi.fn>; connected: boolean }>, configs: [] as Array<Record<string, unknown>> }));

vi.mock("@stomp/stompjs", () => ({
  ReconnectionTimeMode: { EXPONENTIAL: "EXPONENTIAL" },
  Client: vi.fn(function (this: Record<string, unknown>, config: Record<string, unknown>) {
    const client = { ...config, connected: true, activate: vi.fn(), deactivate: vi.fn().mockResolvedValue(undefined), subscribe: vi.fn(), publish: vi.fn(), connectHeaders: {} };
    mocks.clients.push(client); mocks.configs.push(config); return client;
  }),
}));
vi.mock("sockjs-client", () => ({ default: vi.fn() }));
vi.mock("../auth/tokenStorage", () => ({ getAccessToken: () => "token" }));
vi.mock("../api/httpClient", () => ({ getApiBaseUrl: () => "http://localhost:8080", ensureFreshToken: vi.fn() }));

const Harness = ({ id }: { id: number | null }) => {
  useConversationSocket({ conversationId: id, onMessage: vi.fn(), onPresence: vi.fn(), onReconnected: vi.fn() });
  return null;
};

afterEach(() => vi.clearAllMocks());

describe("conversation socket lifecycle", () => {
  beforeEach(() => { mocks.clients.length = 0; mocks.configs.length = 0; });

  it("deactivates the old client on conversation switch and on unmount", async () => {
    const view = render(<Harness id={12} />);
    await waitFor(() => expect(mocks.clients).toHaveLength(1));
    const first = mocks.clients[0];
    view.rerender(<Harness id={13} />);
    await waitFor(() => expect(first.deactivate).toHaveBeenCalledOnce());
    await waitFor(() => expect(mocks.clients).toHaveLength(2));
    const second = mocks.clients[1];
    view.unmount();
    expect(second.deactivate).toHaveBeenCalledOnce();
  });

  it("uses the exact authenticated topic and send destinations", async () => {
    render(<Harness id={12} />);
    await waitFor(() => expect(mocks.configs).toHaveLength(1));
    const config = mocks.configs[0] as { beforeConnect: () => void; onConnect: () => void };
    config.beforeConnect(); config.onConnect();
    expect((mocks.clients[0] as unknown as { connectHeaders: unknown }).connectHeaders).toEqual({ Authorization: "Bearer token" });
    expect(mocks.clients[0].subscribe).toHaveBeenCalledWith("/topic/conversations/12", expect.any(Function));
    expect(mocks.clients[0].subscribe).toHaveBeenCalledWith("/topic/conversations/12/presence", expect.any(Function));
  });
});
