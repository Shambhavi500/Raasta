# Raasta design guide

This guide documents the Android app's existing design system. It is based on the Compose theme and screens in the checkout, not on the unrelated root-level `design.md`.

## Product character

Raasta is a map-led, dark-first trip companion for travel that may happen at night. It should make the next decision easy to find, make a recommendation explainable, and make estimates visibly different from known facts. The green-lime accent belongs to actions and selected states; amber marks caution or incomplete evidence; red is reserved for urgent or destructive states.

The interface uses Material 3 components and Compose layouts. On map screens, the map remains visible above a draggable/contained bottom sheet. Planning steps use full-screen, vertically scrollable content with a persistent primary action near the bottom. Route and journey cards use short headings, mode icons, source/caution labels, and leg-by-leg detail.

## Color tokens

Tokens are defined in `app/app/src/main/java/com/raasta/app/ui/theme/Color.kt` and mapped in `Theme.kt`.

| Role | Token | Value | Use |
|---|---|---|---|
| Brand action/highlight | `ElectricLime` | `#D4F639` | Primary actions, selected goals, positive selection |
| Main dark surface | `Obsidian` | `#151618` | Default background and dark mode |
| Light surface | `Eggshell` | `#F4F4F0` | Optional light theme |
| Light card | `CardWhite` | `#FFFFFF` | Light surfaces |
| Danger | `DangerRed` | `#DC2626` | Destructive/urgent action |
| Caution | `AttentionAmber` | `#F59E0B` | Data uncertainty and warnings |
| Positive status | `SuccessGreen` | `#10B981` | Confirmed/positive status |
| Secondary text | `MutedSlate` | `#76787E` | Light-theme secondary text |
| Raised dark surface | `ObsidianRaised` | `#1F2124` | Dark cards and sheets |
| Highest dark surface | `ObsidianHigh` | `#2A2C30` | Nested raised surfaces |
| Dark secondary text | `MutedOnDark` | `#A3A6AD` | Body copy on dark surfaces |
| Metro mode | `MetroAqua` | `#38BDF8` | Metro tags and route cues |

The app defaults to dark mode. `RaastaTheme` also defines a light palette, though the entry point currently uses the dark default.

## Type and shape

The app uses Material 3's default type family, with heavier headings and a larger 17 sp body size for reading while travelling. The core corner sizes are 8, 16, 24, and 32 dp; some prominent surfaces use 34 dp. Buttons use Material 3's pill shape. Primary actions in journey stages are typically full-width and 56 dp tall.

## Screen patterns

- **Home/map:** map as the canvas; trip fields, time control, and one primary Continue action in a bottom sheet.
- **Search:** separate From and To editing; suggestions are restricted to the bundled corridor rather than arbitrary geocoding.
- **Preferences:** selectable goal cards and switches; a fixed Find routes action.
- **Data check:** staged source and status rows use labels for OK, estimate, unknown, or outdated data.
- **Compare/details:** route cards show recommendation, journey metrics, trade-offs, and leg-level cautions. The map accompanies route selection.
- **Journey:** map and current step stay prominent; status, boarding confirmation, pause/skip controls, report-problem action, and SOS remain in the journey context.
- **Feedback/reports:** report status is explicit; unverified reports are not planner evidence.

## Evidence and status language

Preserve these distinctions in future UI work:

| State | Presentation |
|---|---|
| Sourced value | Name its source and retrieval date where available; keep fare/time basis visible. |
| Estimate | Use “estimated” or “assumed” next to the value or its explanation. |
| Synthetic demo data | Label it as synthetic in the place where a rider sees it; do not let the display imply a real-world incident. |
| Unknown/unavailable | Say what is not known and what still works without it. Do not substitute an invented live value. |
| Rider report | Show whether it is unverified, verified, or rejected, and who can actually review it in this demo. |
| Safety score | Explain that it is a ranking estimate from incomplete mapped data and is not a guarantee. |

### Known mismatch to resolve

`crime_zones.json` labels its 30 zones synthetic, and the app marks street safety partial when that layer is present. The current `SafetyModel` nevertheless reads the layer into `crimeLevels` and uses it in risk and route cost. The UI must not imply that those demo zones are evidence about real neighbourhood crime. The previous proposed change to exclude demo zones was reverted and is not part of the current branch. Fix this behavior before presenting the safety ranking as evidence-first.

## Responsive and accessibility notes

The UI is designed for a phone-sized portrait viewport, scrollable stage content, and persistent bottom actions. Accessibility preference currently reweights walking and transfers because step-free station/vehicle data is unavailable; it does not establish that a route is wheelchair accessible. Keep icons paired with text or content descriptions, preserve scalable text, and avoid relying on color alone for route/status meaning.

## Source files

- Colors, theme, shapes, type: `app/app/src/main/java/com/raasta/app/ui/theme/`
- Shared panels and map controls: `app/app/src/main/java/com/raasta/app/ui/common/Panels.kt`
- Screen implementations: `app/app/src/main/java/com/raasta/app/ui/`
- User-facing strings: `app/app/src/main/res/values/strings.xml`
