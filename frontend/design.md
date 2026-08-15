---
name: Academic Prestige
colors:
  surface: '#fbf9f9'
  surface-dim: '#dbdad9'
  surface-bright: '#fbf9f9'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f5f3f3'
  surface-container: '#efeded'
  surface-container-high: '#e9e8e7'
  surface-container-highest: '#e4e2e2'
  on-surface: '#1b1c1c'
  on-surface-variant: '#45464e'
  inverse-surface: '#303031'
  inverse-on-surface: '#f2f0f0'
  outline: '#75777f'
  outline-variant: '#c6c6cf'
  surface-tint: '#505d84'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#0a1a3d'
  on-primary-container: '#7583ac'
  inverse-primary: '#b8c5f2'
  secondary: '#78591b'
  on-secondary: '#ffffff'
  secondary-container: '#ffd48a'
  on-secondary-container: '#795a1c'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#1a1c1c'
  on-tertiary-container: '#838484'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dae2ff'
  primary-fixed-dim: '#b8c5f2'
  on-primary-fixed: '#0a1a3d'
  on-primary-fixed-variant: '#38466b'
  secondary-fixed: '#ffdea9'
  secondary-fixed-dim: '#eac078'
  on-secondary-fixed: '#271900'
  on-secondary-fixed-variant: '#5e4203'
  tertiary-fixed: '#e2e2e2'
  tertiary-fixed-dim: '#c6c6c7'
  on-tertiary-fixed: '#1a1c1c'
  on-tertiary-fixed-variant: '#454747'
  background: '#fbf9f9'
  on-background: '#1b1c1c'
  surface-variant: '#e4e2e2'
typography:
  display-lg:
    fontFamily: Righteous
    fontSize: 64px
    fontWeight: '400'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Righteous
    fontSize: 40px
    fontWeight: '400'
    lineHeight: '1.2'
  headline-xl:
    fontFamily: Righteous
    fontSize: 48px
    fontWeight: '400'
    lineHeight: '1.2'
  headline-md:
    fontFamily: Righteous
    fontSize: 32px
    fontWeight: '400'
    lineHeight: '1.3'
  body-lg:
    fontFamily: Afacad
    fontSize: 20px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Afacad
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  label-sm:
    fontFamily: Afacad
    fontSize: 12px
    fontWeight: '600'
    lineHeight: '1.2'
    letterSpacing: 0.1em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  section-gap: 120px
  container-margin: 64px
  gutter: 24px
  safe-area: 32px
---

## Brand & Style

The design system is anchored in the concept of "Academic Prestige"—a fusion of timeless luxury and modern educational innovation. It targets a high-end demographic that values precision, clarity, and the quiet confidence of excellence.

The visual style is **Minimalist-Luxury**, borrowing the editorial weight of a premium magazine and the technical exactness of industry-leading hardware. The interface prioritizes generous whitespace to reduce cognitive load, allowing the high-contrast palette and distinctive typography to command attention.

The emotional response is one of trust and aspiration. By utilizing "Apple-like" precision—perfectly aligned grids, subtle depth, and intentional use of motion—the UI transforms a functional learning platform into a curated educational experience.

## Colors

The palette is restricted to three core tones to maintain a high-fashion, editorial feel, now updated with a deep, scholarly navy.

- **Midnight Navy (#000E32):** Replacing pure black as the primary anchor, this deep ink-blue is used for typography, primary action buttons, and structural elements. It provides a more sophisticated, academic grounding than neutral black.
- **Primary Gold (#C9A25D):** Reserved for high-value accents, brand identifiers, and selective call-to-actions. It signifies quality and achievement.
- **Soft White (#F5F5F5):** The canvas for the entire system. This off-white shade reduces eye strain compared to pure white while maintaining a crisp, clean aesthetic.
- **Neutral Grey (#707070):** Utilized strictly for secondary body text and metadata to maintain hierarchy without cluttering the visual field.

## Typography

The typography strategy leverages the geometric uniqueness of **Righteous** for headings to create an immediate, memorable brand voice. Its rounded, stylized forms contrast beautifully against the sleek, modern humanist qualities of **Afacad**.

**Afacad** is used for all functional text. Its high legibility ensures that complex educational content remains accessible.

- **Display levels** use tight letter spacing to feel like custom-set type.
- **Body levels** utilize a generous 1.6 line-height to maximize readability in long-form text.
- **Labels** are often set in uppercase with increased tracking (letter spacing) to denote importance and structure within navigation and metadata.

## Layout & Spacing

This design system employs a **Fixed Grid** philosophy for desktop to maintain strict editorial control, transitioning to a fluid model for mobile devices.

- **Desktop (1440px+):** 12-column grid with 64px margins and 24px gutters. Content is typically center-aligned within a max-width container of 1280px.
- **Tablet (768px - 1439px):** 8-column grid with 32px margins.
- **Mobile (<768px):** 4-column fluid grid with 20px margins.

The spacing rhythm is "extra-airy." Vertical gaps between sections are intentionally large (120px+) to allow the user to focus on one concept at a time. Internal padding within components should never fall below the 20px threshold to maintain the "luxury" feel of space.

## Elevation & Depth

Visual hierarchy is achieved through **Tonal Layers** and **Ambient Shadows**.

1.  **Surfaces:** The base layer is `Soft White`. Secondary containers or "cards" use pure white (#FFFFFF) to subtly lift off the background.
2.  **Shadows:** Shadows are rarely used for borders; instead, they are ultra-diffused "light-leaks." Use a blur radius of 40px-60px with a very low opacity (3-5%) navy or a slight gold tint to create a soft glow rather than a hard drop shadow.
3.  **Active States:** When an element is interacted with, it should gain a subtle internal glow or a 1px Primary Gold border to indicate focus.
4.  **Glassmorphism:** Use sparingly for navigation overlays. A 20px backdrop blur with 80% opacity `Soft White` keeps the content beneath visible but non-distracting.

## Shapes

The shape language is defined by "exaggerated softness." While the brand is professional, the corner radii follow a balanced 0.5rem (8px) base increment to make the interface feel approachable and modern.

- **Standard Components (Buttons, Inputs):** 8px (sm) to 12px (md) radius.
- **Container Cards:** 16px (lg) to 24px (xl) radius.
- **Media (Images/Video):** Should match the 24px container radius to feel integrated into the layout.
- **Accents:** Occasional use of perfect circles for icons or badges to contrast the pill-like rectangles.

## Components

### Buttons
- **Primary:** Solid Midnight Navy background, White text. No border. 8px rounded corners.
- **Secondary:** Transparent background, 1.5px Midnight Navy border.
- **Action/Accent:** Primary Gold background with Midnight Navy text, used only for "Conversion" moments.

### Cards
Cards should have no visible border. Use the `Soft White` on pure White logic or a very subtle 5% opacity shadow. Internal padding should be 40px for desktop to maintain the editorial feel.

### Input Fields
Inputs are minimalist: 1px bottom-border only (#000E32) or a fully enclosed field with #F5F5F5 background and 8px radius. Labels should float or sit above in the `label-sm` style.

### Navigation
The header should be high-profile. Use the `Righteous` font for the logo and `Afacad` for links. Links should have a Primary Gold underline on hover to provide a premium feedback loop.

### Chips/Badges
Small, pill-shaped elements with a Primary Gold background and 10% opacity, creating a "tinted" effect that highlights content without overpowering the text.