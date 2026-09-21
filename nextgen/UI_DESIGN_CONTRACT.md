# Project Superhuman UI Alignment Contract

This contract applies to new and revised NextGen UI.

## Alignment grid
- Use `SuperhumanLayout` tokens from `SuperhumanTheme.kt` instead of one-off spacing for peer elements.
- Page horizontal inset: `pageHorizontal`.
- Page vertical inset: `pageVertical`.
- Major section rhythm: `sectionGap`.
- Standard card inset: `cardPadding`.
- Compact/internal card inset: `compactCardPadding`.
- Standard content and row gaps: `contentGap` / `compactGap`.

## Controls
- Navigation/icon controls use `iconTouchTarget` so visible icons and touch targets stay centered and consistent.
- Segmented controls use `controlHeight`, `segmentedPadding`, and `segmentedGap`.
- Never center a title/date by relying on `Arrangement.SpaceBetween` when the left and right controls can have different visual widths. Use an overlay/Box with an independently centered middle element.

## Cards and repeated components
- Peer cards should use identical outer padding and corner radii.
- Repeated metric cards should use consistent dimensions where variable text would otherwise make shells look uneven.
- Text inside repeated cards should occupy predictable slots (for example, a fixed-height two-line label area) so values align across the row.
- Adjacent cards should share the same baseline, padding, and gap tokens.

## Visual quality checks
Before merging UI changes, verify:
1. Left and right page edges align across sections.
2. Peer cards have equal internal padding.
3. Icons are optically centered within fixed touch targets.
4. Labels, values and charts align across repeated elements.
5. No single component introduces arbitrary spacing that breaks the surrounding grid.
6. Scrollable rows retain visible breathing room at both ends.
7. Dark and light themes preserve the same geometry.

These rules are part of the Project Superhuman visual system, not module-specific preferences.
