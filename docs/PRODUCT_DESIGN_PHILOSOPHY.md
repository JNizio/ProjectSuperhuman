# Project Superhuman — Product Design Philosophy

**Applies to the entire Android application, not just Home.** This is a product
contract for future features, redesigns, refactors, and contributions.

## Priorities, in order

1. **Accessibility and comprehension.** People should understand what is happening and
   what to do next without reading tiny labels, interpreting decoration, or scrolling
   through empty states.
2. **Optical alignment and consistent, decent-sized typography.** Text baselines,
   metric columns, corner insets, cards, controls and gutters align to one grid.
3. **A quiet, premium, matte visual language.** Deliberate contrast, restrained colour,
   continuous-feeling curves, subtle optical edge highlights, and calm movement.
   No gratuitous glow, photo-backed metric cards or loud gradients.
4. **Purposeful UX before data density.** Show the most actionable daily information first.
   Never remove vital context, alter a recorded value, or conceal a clinically relevant warning
   merely to make a screen prettier. Use hierarchy and disclosure for deeper detail.
5. **Consistency across modules.** Each new screen should feel like part of the same system.

## Practical layout rules

- Use `SuperhumanLayout` tokens for dimensions and spacing, rather than improvising
  a new grid on each page. Home cards use the shared `premiumHomeTile` modifier.
- Default Home order: **Nutrition → Training → Sleep**, then the other draggable
  modules. Store subsequent user reordering and honour it. A one-time migration to
  the new default is intentional.
- On a typical phone at normal font scale, these three primary modules should
  appear in full above the initial fold. **Never enforce this by clipping content.**
  On smaller screens, landscape and large font/accessibility settings, support
  natural heights and scrolling instead.
- Show the key data in each tile: Nutrition = calories, goal progress when set,
  protein/carbohydrates/fat; Training = sessions, total time, strength and cardio;
  Sleep = duration, score, status and a concise recovery insight or sync state.
- Prefer around **20 dp screen gutters**, **12 dp card spacing**, **16–20 dp inner
  padding**, **24–26 dp outer corner radii**, and 12–16 dp inner radii.
- Text should use **12 sp or larger for normal supporting information**, 14–17 sp
  for important values, and 25–32 sp for primary metrics. Labels may be modest,
  but should not become tiny all-caps microtext. Text must scale with Android
  font preferences (sp), wrap where needed and preserve line height.
- Tap targets should be at least **48 dp** along both axes where possible.
  Entire Home cards are navigable. Do not make users aim for decorative arrows.
- Unavailable readings must say they are missing or invite a sync; do not
  imply a real measurement, fabricate scores, or draw decorative clinical traces
  that resemble actual data.

## Visual and interaction principles

- Build depth through a subtle matte tonal shift, one optical hairline and
  restrained elevation. Avoid thick contrasting borders, noisy backgrounds and
  nested pill shapes unless the inner surface conveys hierarchy.
- Use accent colour to distinguish actions, active states and health domains,
  not to fill every card. The surface and typography form the system.
- Motion should communicate navigation and changes in state; accommodate system
  reduced-motion preferences where possible. Do not trade responsiveness for flourish.
- Check default/empty/populated states, narrow screens, font scaling, contrast
  and one-handed reach before considering a screen finished.

**Acceptance test:** if a page becomes more visually impressive but less legible,
less aligned or harder to navigate, the change is not an improvement.
