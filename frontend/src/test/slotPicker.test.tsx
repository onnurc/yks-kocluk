import { useState } from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SlotPicker } from "../trial/SlotPicker";
import type { AvailabilityResponse } from "../booking/bookingTypes";

afterEach(cleanup);

const at = (day: number, hour: number) => new Date(2026, 8, day, hour, 0, 0, 0);
const slot = (id: number, day: number, hour: number, booked = false): AvailabilityResponse => ({
  id,
  coachProfileId: 42,
  startTime: at(day, hour).toISOString(),
  endTime: new Date(at(day, hour).getTime() + 30 * 60 * 1000).toISOString(),
  booked,
  purpose: "TRIAL",
});

function PickerHarness({ slots, onConfirm = vi.fn() }: { slots: AvailabilityResponse[]; onConfirm?: () => void }) {
  const [selected, setSelected] = useState<number | null>(null);
  return (
    <SlotPicker
      slots={slots}
      rangeStart={at(1, 0)}
      rangeEnd={at(14, 0)}
      selectedSlotId={selected}
      onSelectSlot={setSelected}
      onConfirm={onConfirm}
      now={at(1, 8)}
    />
  );
}

describe("SlotPicker", () => {
  it("selects the earliest available day but creates no booking until confirmation", () => {
    const confirm = vi.fn();
    render(<PickerHarness slots={[slot(11, 2, 9), slot(12, 2, 10)]} onConfirm={confirm} />);

    expect(screen.getByRole("button", { name: /2 Eylül Çarşamba, 2 uygun saat/i })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Bir saat seç" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "09:00 saatini seç" }));
    expect(confirm).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: /Devam et.*2 Eylül.*09:00/i })).toBeEnabled();

    fireEvent.click(screen.getByRole("button", { name: /Devam et.*2 Eylül.*09:00/i }));
    expect(confirm).toHaveBeenCalledTimes(1);
  });

  it("clears the selected time when the day changes", () => {
    render(<PickerHarness slots={[slot(21, 2, 9), slot(22, 3, 11)]} />);

    fireEvent.click(screen.getByRole("button", { name: "09:00 saatini seç" }));
    expect(screen.getByRole("button", { name: /Devam et/ })).toBeEnabled();
    fireEvent.click(screen.getByRole("button", { name: /3 Eylül Perşembe, 1 uygun saat/i }));

    expect(screen.getByRole("button", { name: "Bir saat seç" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "11:00 saatini seç" })).toHaveAttribute("aria-pressed", "false");
  });

  it("navigates only inside the supported range", () => {
    render(<PickerHarness slots={[slot(31, 2, 9), slot(32, 10, 14)]} />);

    expect(screen.getByRole("button", { name: "Önceki haftayı göster" })).toBeDisabled();
    const next = screen.getByRole("button", { name: "Sonraki haftayı göster" });
    expect(next).toBeEnabled();
    fireEvent.click(next);

    expect(screen.getByRole("button", { name: /10 Eylül Perşembe, 1 uygun saat/i })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "14:00 saatini seç" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sonraki haftayı göster" })).toBeDisabled();
  });

  it("disables past, empty and booked dates without inventing availability", () => {
    render(
      <SlotPicker
        slots={[slot(41, 1, 7), slot(42, 3, 9, true), slot(43, 4, 10)]}
        rangeStart={new Date(2026, 7, 30)}
        rangeEnd={at(5, 0)}
        selectedSlotId={null}
        onSelectSlot={vi.fn()}
        onConfirm={vi.fn()}
        now={at(1, 8)}
      />,
    );

    expect(screen.getByRole("button", { name: /30 Ağustos Pazar, dolu/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /3 Eylül Perşembe, dolu/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /4 Eylül Cuma, 1 uygun saat/i })).toBeEnabled();
  });
});
