// The theme switch: Auto (follow the device, or the app the page is open in), Light or Dark
// (theme.ts keeps and applies it).
//
// ThemeMenu: an icon button at the end of the app bar on every page (sun, moon, or a
// half-filled disc for Auto), named "Theme: Auto" and so on, that opens a three-option menu.
// It follows the WAI-ARIA menu button pattern: Enter, Space or a click opens it on the current
// choice, Down and Up open it on the first or last; in the menu, Up, Down, Home, End and the
// first letter move, Enter or Space picks, Escape closes and returns to the button, and Tab
// or a tap outside closes it.
//
// ThemeChoiceControl: the same three choices as a segmented control (the Data page).

import { useEffect, useId, useRef, useState, type FocusEvent, type KeyboardEvent } from "react";
import { setThemeChoice, THEME_CHOICES, THEME_LABELS, useAutoSource, useThemeChoice, type ThemeChoice } from "../theme";
import { Segmented } from "./Controls";
import { Icon, type IconName } from "./Svg";
import "./theme.css";

export const THEME_ICONS: Record<ThemeChoice, IconName> = { auto: "theme-auto", light: "sun", dark: "moon" };

const HINTS: Record<ThemeChoice, string> = {
  auto: "Follows your device",
  light: "Always light",
  dark: "Always dark",
};
/** Auto's hint when the page is open inside an app that sets its own theme (theme.ts). */
const AUTO_HINT_IN_HOST = "Follows the app it's open in";

/** Scrolling the page this far (px) with the menu open closes it. */
const CLOSE_AFTER_SCROLL = 40;

export function ThemeMenu() {
  const choice = useThemeChoice();
  const autoSource = useAutoSource();
  const [open, setOpen] = useState(false);
  // The item to focus once the menu is open (set by whatever opened it).
  const [startAt, setStartAt] = useState(0);
  const wrap = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const items = useRef<(HTMLButtonElement | null)[]>([]);
  const menuId = useId();
  const current = THEME_CHOICES.indexOf(choice);
  const name = `Theme: ${THEME_LABELS[choice]}`;

  const openAt = (index: number) => {
    setStartAt(index);
    setOpen(true);
  };
  const close = (refocus: boolean) => {
    setOpen(false);
    if (refocus) button.current?.focus({ preventScroll: true });
  };

  useEffect(() => {
    if (open) items.current[startAt]?.focus({ preventScroll: true });
  }, [open, startAt]);

  // A tap or click outside closes the menu, and so does scrolling the page away from it (on a
  // phone the app bar slides out of view as the page scrolls down).
  useEffect(() => {
    if (!open) return;
    const startY = window.scrollY;
    const onPointerDown = (e: PointerEvent) => {
      if (!wrap.current?.contains(e.target as Node)) setOpen(false);
    };
    const onScroll = () => {
      if (Math.abs(window.scrollY - startY) > CLOSE_AFTER_SCROLL) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      window.removeEventListener("scroll", onScroll);
    };
  }, [open]);

  const pick = (c: ThemeChoice) => {
    setThemeChoice(c);
    close(true);
  };

  const onButtonKeyDown = (e: KeyboardEvent<HTMLButtonElement>) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      openAt(e.key === "ArrowDown" ? 0 : THEME_CHOICES.length - 1);
    }
  };

  const onMenuKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const n = THEME_CHOICES.length;
    const at = items.current.findIndex((el) => el === document.activeElement);
    let next = -1;
    if (e.key === "ArrowDown") next = at < 0 ? 0 : (at + 1) % n;
    else if (e.key === "ArrowUp") next = at < 0 ? n - 1 : (at - 1 + n) % n;
    else if (e.key === "Home") next = 0;
    else if (e.key === "End") next = n - 1;
    else if (e.key === "Escape") {
      e.preventDefault();
      e.stopPropagation();
      close(true);
      return;
    } else if (e.key === "Tab") {
      setOpen(false);
      return;
    } else if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
      const letter = e.key.toLowerCase();
      next = THEME_CHOICES.findIndex((c) => THEME_LABELS[c].toLowerCase().startsWith(letter));
    }
    if (next < 0) return;
    e.preventDefault();
    items.current[next]?.focus({ preventScroll: true });
  };

  // Focus moving to something outside (another control, by Tab or by script) closes the menu.
  const onBlur = (e: FocusEvent<HTMLDivElement>) => {
    const to = e.relatedTarget as Node | null;
    if (open && to && !wrap.current?.contains(to)) setOpen(false);
  };

  return (
    <div className="theme-menu" ref={wrap} onBlur={onBlur}>
      <button
        ref={button}
        type="button"
        className="theme-button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        aria-label={name}
        title={name}
        onClick={() => (open ? close(false) : openAt(current < 0 ? 0 : current))}
        onKeyDown={onButtonKeyDown}
      >
        <Icon name={THEME_ICONS[choice]} size={20} />
      </button>
      {open && (
        <div className="theme-pop" id={menuId} role="menu" aria-label="Theme" onKeyDown={onMenuKeyDown}>
          <p className="theme-pop-title" aria-hidden="true">
            Theme
          </p>
          {THEME_CHOICES.map((c, i) => {
            const checked = c === choice;
            return (
              <button
                key={c}
                ref={(el) => {
                  items.current[i] = el;
                }}
                type="button"
                role="menuitemradio"
                aria-checked={checked}
                tabIndex={-1}
                className="theme-item"
                onClick={() => pick(c)}
              >
                <Icon name={THEME_ICONS[c]} size={20} />
                <span className="theme-item-text">
                  <span className="theme-item-label">{THEME_LABELS[c]}</span>
                  <span className="theme-item-hint">
                    {c === "auto" && autoSource === "host" ? AUTO_HINT_IN_HOST : HINTS[c]}
                  </span>
                </span>
                <span className="theme-item-tick">{checked && <Icon name="check" size={18} />}</span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

/** The three choices as a segmented control, for a page of settings (the Data page). */
export function ThemeChoiceControl() {
  const choice = useThemeChoice();
  return (
    <Segmented<ThemeChoice>
      label="Theme"
      value={choice}
      onChange={setThemeChoice}
      options={THEME_CHOICES.map((c) => ({
        value: c,
        label: (
          <span className="theme-seg-option">
            <Icon name={THEME_ICONS[c]} size={18} />
            {THEME_LABELS[c]}
          </span>
        ),
      }))}
    />
  );
}
