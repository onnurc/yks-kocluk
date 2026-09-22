import { useMemo, useState } from "react";
import type { AvailabilityResponse } from "../booking/bookingTypes";

const DAY_COUNT = 7;

type SlotPickerProps = {
  slots: AvailabilityResponse[];
  rangeStart: Date;
  rangeEnd: Date;
  selectedSlotId: number | null;
  onSelectSlot: (slotId: number | null) => void;
  onConfirm: () => void;
  submitting?: boolean;
  now?: Date;
};

type TimeGroup = { label: string; slots: AvailabilityResponse[] };

function startOfDay(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate());
}

function addDays(value: Date, amount: number) {
  const next = new Date(value);
  next.setDate(next.getDate() + amount);
  return next;
}

function dateKey(value: Date) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" });
}

function monthTitle(first: Date, last: Date) {
  const firstMonth = first.toLocaleDateString("tr-TR", { month: "long" });
  const lastMonth = last.toLocaleDateString("tr-TR", { month: "long" });
  if (first.getMonth() === last.getMonth() && first.getFullYear() === last.getFullYear()) {
    return first.toLocaleDateString("tr-TR", { month: "long", year: "numeric" });
  }
  if (first.getFullYear() === last.getFullYear()) return `${firstMonth} – ${lastMonth} ${last.getFullYear()}`;
  return `${firstMonth} ${first.getFullYear()} – ${lastMonth} ${last.getFullYear()}`;
}

function groupSlots(slots: AvailabilityResponse[]): TimeGroup[] {
  const groups: TimeGroup[] = [
    { label: "Sabah", slots: [] },
    { label: "Öğleden sonra", slots: [] },
    { label: "Akşam", slots: [] },
  ];
  slots.forEach((slot) => {
    const hour = new Date(slot.startTime).getHours();
    groups[hour < 12 ? 0 : hour < 18 ? 1 : 2].slots.push(slot);
  });
  return groups.filter((group) => group.slots.length > 0);
}

export function SlotPicker({
  slots,
  rangeStart,
  rangeEnd,
  selectedSlotId,
  onSelectSlot,
  onConfirm,
  submitting = false,
  now = new Date(),
}: SlotPickerProps) {
  const firstSupportedDay = startOfDay(rangeStart);
  const lastSupportedDay = startOfDay(rangeEnd);
  const today = startOfDay(now);
  const selectableSlots = useMemo(
    () => slots
      .filter((slot) => !slot.booked && new Date(slot.startTime) > now)
      .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime()),
    [now, slots],
  );
  const slotsByDay = useMemo(() => {
    const grouped = new Map<string, AvailabilityResponse[]>();
    selectableSlots.forEach((slot) => {
      const key = dateKey(new Date(slot.startTime));
      grouped.set(key, [...(grouped.get(key) ?? []), slot]);
    });
    return grouped;
  }, [selectableSlots]);
  const earliestAvailableDay = selectableSlots[0] ? dateKey(new Date(selectableSlots[0].startTime)) : null;
  const earliestAvailableDate = selectableSlots[0] ? startOfDay(new Date(selectableSlots[0].startTime)) : null;
  const [viewStart, setViewStart] = useState(
    earliestAvailableDate && earliestAvailableDate > addDays(firstSupportedDay, DAY_COUNT - 1)
      ? earliestAvailableDate
      : firstSupportedDay,
  );
  const [selectedDay, setSelectedDay] = useState<string | null>(earliestAvailableDay);
  const activeSelectedDay = selectedDay && slotsByDay.has(selectedDay) ? selectedDay : earliestAvailableDay;

  const days = Array.from({ length: DAY_COUNT }, (_, index) => addDays(viewStart, index));
  const visibleLastDay = days[days.length - 1];
  const canGoPrevious = viewStart > firstSupportedDay;
  const canGoNext = visibleLastDay < lastSupportedDay;
  const selectedSlots = activeSelectedDay ? slotsByDay.get(activeSelectedDay) ?? [] : [];
  const selectedDate = activeSelectedDay ? new Date(`${activeSelectedDay}T12:00:00`) : null;
  const selectedSlot = selectableSlots.find((slot) => slot.id === selectedSlotId) ?? null;
  const groups = selectedSlots.length > 12 ? groupSlots(selectedSlots) : [{ label: "", slots: selectedSlots }];

  const moveWeek = (direction: -1 | 1) => {
    const candidate = addDays(viewStart, direction * DAY_COUNT);
    const bounded = direction < 0 && candidate < firstSupportedDay
      ? firstSupportedDay
      : direction > 0 && candidate > lastSupportedDay
        ? lastSupportedDay
        : candidate;
    setViewStart(bounded);
    const firstAvailable = days
      .map((_, index) => addDays(bounded, index))
      .find((day) => slotsByDay.has(dateKey(day)) && day >= today && day <= lastSupportedDay);
    setSelectedDay(firstAvailable ? dateKey(firstAvailable) : null);
    onSelectSlot(null);
  };

  return (
    <div className="slot-picker">
      <div className="slot-picker__calendar-header">
        <button type="button" aria-label="Önceki haftayı göster" onClick={() => moveWeek(-1)} disabled={!canGoPrevious}>←</button>
        <strong>{monthTitle(viewStart, visibleLastDay)}</strong>
        <button type="button" aria-label="Sonraki haftayı göster" onClick={() => moveWeek(1)} disabled={!canGoNext}>→</button>
      </div>

      <div className="slot-picker__days" aria-label="Görüşme günü seçin">
        {days.map((day) => {
          const key = dateKey(day);
          const count = slotsByDay.get(key)?.length ?? 0;
          const outsideRange = day < firstSupportedDay || day > lastSupportedDay;
          const disabled = outsideRange || day < today || count === 0;
          const selected = key === activeSelectedDay;
          const fullDate = day.toLocaleDateString("tr-TR", { day: "numeric", month: "long", weekday: "long" });
          return (
            <button
              key={key}
              type="button"
              className={selected ? "slot-picker__day slot-picker__day--selected" : "slot-picker__day"}
              aria-label={`${fullDate}, ${count ? `${count} uygun saat` : "dolu"}`}
              aria-pressed={selected}
              disabled={disabled}
              onClick={() => {
                setSelectedDay(key);
                onSelectSlot(null);
              }}
            >
              <span>{day.toLocaleDateString("tr-TR", { weekday: "short" }).replace(".", "")}</span>
              <strong>{day.getDate()}</strong>
              <small>{count ? `${count} saat` : "Dolu"}</small>
            </button>
          );
        })}
      </div>

      {selectedDate ? (
        <div className="slot-picker__times">
          <div className="slot-picker__times-heading">
            <strong>{selectedDate.toLocaleDateString("tr-TR", { day: "numeric", month: "long", weekday: "long" })}</strong>
            <span>{selectedSlots.length} uygun saat</span>
          </div>
          {selectedSlots.length ? (
            <div className="slot-picker__time-scroll">
              {groups.map((group) => (
                <section className="slot-picker__time-group" key={group.label || "all"} aria-label={group.label || "Uygun saatler"}>
                  {group.label && <h4>{group.label}</h4>}
                  <div className="slot-picker__time-grid">
                    {group.slots.map((slot) => {
                      const selected = slot.id === selectedSlotId;
                      return (
                        <button
                          key={slot.id}
                          type="button"
                          aria-label={`${formatTime(slot.startTime)} saatini seç`}
                          aria-pressed={selected}
                          className={selected ? "slot-picker__time slot-picker__time--selected" : "slot-picker__time"}
                          onClick={() => onSelectSlot(slot.id)}
                        >
                          {formatTime(slot.startTime)}
                        </button>
                      );
                    })}
                  </div>
                </section>
              ))}
            </div>
          ) : <p className="slot-picker__empty">Bu gün için uygun saat bulunmuyor.</p>}
        </div>
      ) : <p className="slot-picker__empty">Bu haftada seçilebilir bir gün bulunmuyor.</p>}

      <button className="trial-card__submit" type="button" onClick={onConfirm} disabled={!selectedSlot || submitting}>
        {submitting
          ? "Gönderiliyor…"
          : selectedSlot
            ? `Devam et · ${new Date(selectedSlot.startTime).toLocaleDateString("tr-TR", { day: "numeric", month: "long" })}, ${formatTime(selectedSlot.startTime)}`
            : "Bir saat seç"}
      </button>
    </div>
  );
}
