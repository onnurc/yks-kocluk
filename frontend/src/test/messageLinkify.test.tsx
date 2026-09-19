import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { linkifyMessage } from "../messaging/linkifyMessage";

/**
 * Chat is where coaches now share Google Meet links, so message text becomes clickable. The
 * point of these tests is the boundary: http(s) becomes an anchor, everything else stays inert
 * text, and no message can inject markup (the util returns React nodes, never raw HTML).
 */
const renderContent = (content: string) =>
  render(<span data-testid="bubble">{linkifyMessage(content)}</span>);

afterEach(cleanup);

describe("linkifyMessage", () => {
  it("renders an https URL as a safe new-tab link", () => {
    renderContent("Görüşme linki: https://meet.google.com/abc-defg-hij");

    const link = screen.getByRole("link", { name: "https://meet.google.com/abc-defg-hij" });
    expect(link).toHaveAttribute("href", "https://meet.google.com/abc-defg-hij");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("links http URLs too and keeps the surrounding text", () => {
    renderContent("önce http://example.com sonra");

    expect(screen.getByRole("link", { name: "http://example.com" })).toBeInTheDocument();
    expect(screen.getByTestId("bubble")).toHaveTextContent("önce http://example.com sonra");
  });

  it("never links a javascript: scheme", () => {
    renderContent("javascript:alert(1)");

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByTestId("bubble")).toHaveTextContent("javascript:alert(1)");
  });

  it("never links data: or bare www. text", () => {
    renderContent("data:text/html,<script>alert(1)</script> ve www.example.com");

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("does not interpret HTML in a message as markup", () => {
    renderContent('<img src=x onerror="alert(1)"> <b>kalın</b>');

    const bubble = screen.getByTestId("bubble");
    expect(bubble.querySelector("img")).toBeNull();
    expect(bubble.querySelector("b")).toBeNull();
    // It survives as literal text instead.
    expect(bubble).toHaveTextContent('<img src=x onerror="alert(1)"> <b>kalın</b>');
  });

  it("does not swallow sentence punctuation into the href", () => {
    renderContent("Buradan katıl: https://meet.google.com/abc-defg-hij.");

    expect(screen.getByRole("link", { name: "https://meet.google.com/abc-defg-hij" }))
      .toHaveAttribute("href", "https://meet.google.com/abc-defg-hij");
    expect(screen.getByTestId("bubble")).toHaveTextContent(
      "Buradan katıl: https://meet.google.com/abc-defg-hij.",
    );
  });

  it("links every URL in a multi-link message", () => {
    renderContent("https://a.example/one ve https://b.example/two");

    expect(screen.getAllByRole("link")).toHaveLength(2);
  });

  it("leaves a message without URLs untouched", () => {
    renderContent("Yarın görüşürüz");

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByTestId("bubble")).toHaveTextContent("Yarın görüşürüz");
  });

  it("does not link a bare scheme with no host", () => {
    renderContent("https:// yazdım ama adres yok");

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
