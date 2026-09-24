# IronLog Board Design Spec

## Tokens

```css
:root {
  --bg:      oklch(18% 0.015 265);
  --surface: oklch(23% 0.02 265);
  --fg:      oklch(96% 0.01 90);
  --muted:   oklch(72% 0.02 260);
  --border:  oklch(34% 0.02 260);
  --accent:  oklch(72% 0.16 57);
}
```

Display uses `Avenir Next`, body uses the native system stack, and numeric annotations use `SF Mono`/`Menlo`.

## Observed rules

- Anthracite surfaces create a calm, high-contrast training cockpit.
- Warm orange is reserved for the active state and one primary action per screen.
- Strong tabular numerics make weight, repetitions, volume and completion easy to scan.
- Android boards use compact app bars and bottom navigation; iOS uses safe-area spacing, segmented controls and sheet-like surfaces.
- Photos stay complete and proportional on the left; the right side is a deliberately detailed phone proposal rather than a raster mockup.

**Summary:** IronLog is presented as a precise, dark training cockpit where orange marks only the next decision.
