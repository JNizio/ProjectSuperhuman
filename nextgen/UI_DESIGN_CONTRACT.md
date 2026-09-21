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
- Interactive controls should use no-ripple Project Superhuman interaction helpers unless a ripple is intentionally part of the component.
- Important tap targets should remain at least 44dp even when the visible icon is smaller.

## Accessible type hierarchy
- Primary section titles should normally be 18sp or larger.
- Primary item names should normally be 12–14sp or larger.
- Secondary stats and metadata should normally be at least 10sp when they carry useful information.
- Avoid 7–8sp text for meaningful diary, metric, action, or navigation content; reserve very small type for non-essential labels only.
- Repeated actions such as edit, add, delete, expand and collapse need clear icons and generous hit targets.
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
