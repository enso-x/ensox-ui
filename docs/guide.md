# EnsoX UI guide

EnsoX UI is a widget, layout and painting framework for Minecraft screens. It
depends only on Minecraft (Mojang names) and the loader's resource reload hook;
it has no code of any mod that uses it. How to add it to a mod and the
versioning rules are in the [README](../README.md).

## Packages

| Package | Contents |
|---|---|
| `com.ensox.base` | value types: `ColorX`, `Insets`, `Size`, `Point`, `Easing`, `CubicBezier`, `Animatable` |
| `com.ensox.ui.core` | `Widget`, `Container`, `Flow`/`Row`/`Column`, `Style`, `UiRoot`, `UiScreen`, `FocusManager`, `TransformContainer` |
| `com.ensox.ui.layout` | `Sizing`, `Position`, `Align`, `RadialLayout` |
| `com.ensox.ui.geom` | `Rect`, `UiTransform` |
| `com.ensox.ui.paint` | `Canvas`, `Paint`, `TextureRegion`, `NineSlice`, `ImageFit`, `TextFit`, custom GUI elements (`element/`) |
| `com.ensox.ui.asset` | asset library: `AssetKey`, `AssetLibrary`, `AssetDefinitions`, `UiAsset` |
| `com.ensox.ui.event` | `PointerEvent`, `ScrollEvent`, `KeyEvent`, `TextEvent`, `Modifiers` |
| `com.ensox.ui.state` | `Signal`, `Lens`, `Subscription` |
| `com.ensox.ui.text` | `TextEditor`, `TextLineView` |
| `com.ensox.ui.anim` | `Animated`, `FrameTask` |
| `com.ensox.ui.widget` | `Label`, `Button`, `TextField`, `NumberField`, `Slider`, `Toggle`, `Select`, `Popup`, `ScrollView`, `ReorderableGrid`, ... |

The library ships its own access transformer / access widener for
`GuiGraphicsExtractor.guiRenderState`, `scissorStack` and the `ScissorStack`
class (custom GUI elements and clipping). Mods do not declare them.

## Sharing the library between mods

Several mods nest the library and the loader keeps one copy, so nothing in it
belongs to a single mod:

- **Tooltip style is per screen.** `UiScreen#tooltipStyle()` returns the
  sprite style of the screen's widget tooltips (`null`: vanilla); a mod
  overrides it in its own base screen. `UiRoot#tooltipStyle(id)` sets it on a
  root directly.
- **Asset keys always name their namespace.** There is no default namespace:
  `AssetKey.of(namespace, path)`, `AssetKey.icon(namespace, name)`
  (`<namespace>:icon/<name>`), `AssetLibrary.icon(namespace, name)`. A mod
  usually wraps these in a small helper with its own namespace.
- **Asset reload is the library's.** It registers one client reload listener
  (`AssetReloadListener`); mods only ship their `ui/assets.json`.

## Why no input mixins

Vanilla already routes every mouse button, drag, two-axis scroll, key and
character event (with modifiers and double-click info) to the active
`Screen`. `UiScreen` forwards them to `UiRoot`. No `MouseHandler`,
`KeyboardHandler` or widget mixins are needed, the screen stack, pause and
narrator keep working, and vanilla widgets elsewhere are untouched.

## Writing screens

```java
public final class MyScreen extends UiScreen {
    protected Widget build() {
        return Column.of(header(), body())
            .style(PANEL, s -> s.fill().maxSize(390, 208));
    }
}
```

- The tree is built once. Resize re-lays it out; leaving the screen detaches
  it (bindings close), returning re-attaches it with state intact.
- Reusable styles are `Consumer<Style>` constants applied with
  `widget.style(STYLE, s -> ...)`.
- State lives in `Signal`s; `widget.bind(signal, apply)` updates the widget
  and is closed automatically when the widget leaves the tree.
- `style(...)` returns an inferred type (`<W extends Widget> W`) so chains keep
  their concrete type. Never add an overload next to a method that takes
  such an expression: `Container.add(Widget...)` has no list overload; lists
  go through `addAll(List)` / `replaceChildren(List)`. (A former
  `add(List)` overload made `root.add(pane.style(...))` compile to the list
  variant and crash at runtime with a `ClassCastException`.)

## Writing widgets

Subclass `Widget` (or `Container`) and override only what is needed:
`contentWidth()` / `contentHeight(int)` for `Sizing.content()`, `paint(...)`,
`onPointer/onScroll/onKey/onText`, `onAttach/onDetach`. A `Button` with custom
painting only overrides `paint`.

## Layout

Two passes per invalidation: `measure` (sizes) then `arrange` (positions).
Layout runs once before a frame if any style or content change invalidated it.

| Sizing | Meaning |
|---|---|
| `fixed(n)` | exact |
| `content()` / `content(extra)` | content + padding (+ extra on both sides) |
| `fill(p)` | `round(p% * offered space)` |
| `grow()` / `grow(w)` | like CSS `flex-grow`: the main-axis space left after non-growing flow siblings and gaps is split between growing siblings by weight (`grow()` = 100); leftover pixels go to the first ones, so the shares sum exactly. On the cross axis (and outside flows) it fills the offered space |

| Position | Meaning |
|---|---|
| `flow()` | placed by the parent flow |
| `offset(dx, dy)` | flow, then shifted |
| `absolute(x, y)` | pixels from the parent content box, outside the flow |
| `anchor(px, py)` | percent of the free space in the content box (`anchor(100, 50)` = right, centered) |
| `fraction(px, py)` | percent of the content box; the widget's corner lands there |

Centering is one rule on both axes: `floorDiv(space - size, 2)`.

Containers clip children to their content box by default; `overflow()`
disables it. Invisible widgets take no space.

`ScrollView` keeps the target and eased scroll offsets at double precision.
Each frame it places the content subtree at the nearest whole GUI pixel, so
sprites, nested scissors and pointer hits use the same origin. This also
applies when a scrollbar drag stops between GUI pixels. Minecraft 26.3
floors the transformed scissor origin and size, so an additional fractional
translation during painting can cut a row off a button.

`RadialLayout` places items on an arc around a point (radial context menus):
neighbouring centres `2r + gap` apart, arc radius
`max(r + clearance, (2r + gap) / (2 sin(span / (2(n - 1)))))`; at screen edges
the arc turns instead of squeezing.

## Input

- Pointer press goes to the widget under the pointer (overlays first). The
  widget that consumes it captures the pointer for drag and release.
- Events run an intercept phase (root to target, `Widget.intercept`) and a
  bubble phase (target to root). An ancestor that consumes a drag in the
  intercept phase takes over the gesture; the previous capturer gets
  `PointerEvent.Type.CANCEL`. `ScrollView` drag-to-scroll uses this, so buttons
  inside scroll views still show their pressed state and never fire after a drag.
- Key and text events go to the focused widget and bubble. Tab / Shift+Tab
  cycle focusable widgets; Escape closes the top overlay.
- Focus follows CSS `:focus-visible`: a click focuses the widget (so Enter
  works afterwards) but only keyboard focus is drawn as a highlight
  (`Widget.isFocusVisible()`, `Button.State.focused()`). Skins must not
  highlight on plain `isFocused()`, or clicked widgets stay lit.
- Cursor and tooltip are inherited from the hovered widget's ancestors and
  use the vanilla cursor API (`GuiGraphics.requestCursor`) and vanilla
  tooltips. Lines wrap at 200 px; the sprite style comes from the screen (see
  "Sharing the library"). Item tooltips keep their own style.
- Text editing lives in `com.ensox.ui.text`: `TextEditor` (text, caret,
  selection, undo/redo, clipboard and caret keys, filter, max length) and
  `TextLineView` (scrolling, selection highlight, blinking caret, pointer to
  index). `TextField` is a widget around the two; widgets that draw editable
  text themselves (for example inline names on a zoomable canvas) use them
  directly instead of reimplementing editing. `TextLineView` does not clip
  (`Canvas.pushClip` ignores the pose transform), it cuts the text to the box.

### Searchable selects

`Select.searchable(minimumOptions, fieldFactory, noResults)` turns the Select's
own field into the filter input while its list is open, once the list reaches
that threshold: a magnifier replaces the value, the factory's `TextField` (frame
removed) takes the text, and `Popup.keepOpenOn` keeps clicks on it from closing
the list. The list then holds only results, with the matched parts lit
(`SelectOptions.matches`, merged ranges; labels that fit only) and a footer
`Esc · visible/total`.

Each open snapshots the current options and indexes the displayed labels plus
their string representations; `searchText(...)` supplies custom IDs or aliases.
Matching ignores case and requires every whitespace-separated term.
`SelectOptions` owns filtering/highlight state and `SelectPopup` owns the
results and the footer. Filtering preserves the input, focus and popup height.
Up/Down navigate matches (`Select` forwards them from its input); Enter commits
the highlighted match; Space types into the filter; Escape cancels. The popup
closes and restores the anchor's focus before notifying a change, so forms that
replace their controls can transfer focus to the replacement.

### Grouped selects (submenus)

`Select.grouped(option -> SelectGroup)` sorts options into categories; a null
group keeps an option in the list itself. Categories keep the order of their
first options, so the option list decides the menu order. The open list shows one
row per category with a right-pointing arrow; hovering it (or Right/Enter)
opens its options in a submenu: another `SelectPopup` in its own overlay, placed
by `Popup.openBeside` right of the list (left when it does not fit), its first
row level with the category and the frames' glows overlapping by
`Look.popupOutset`. `SelectGroup.ruleAbove` draws a dotted rule over a category
row to set a run of categories apart.

- `SelectRows` owns the rows: the category tree, or, while a filter is typed,
  the flat matches as `category › option` (the category's label is searchable).
- The closed field shows the value's path the same way.
- While the pointer moves from the open category towards the submenu, rows it
  crosses do not switch the submenu for 300 ms (`SelectPopup.towardSubmenu`, a
  triangle from the last pointer position on the category to the submenu's
  near edge).
- Left or Escape in a submenu returns to the categories; a click outside every
  list closes them all; choosing an option closes both lists.

The select arrow is vertically centered from the field's actual height, using
the same floor rounding as layout; give arrow sprites symmetric transparent
padding so the visible mark stays centered.

## Reorderable card grids

`ReorderableGrid<T>` is a reusable `ScrollView` over immutable item snapshots.
It owns viewport geometry, virtualization and gestures; the caller owns the
authoritative order and persistence. Items require unique, stable string keys.

```java
ReorderableGrid<Entry> grid = new ReorderableGrid<>(Entry::id, this::buildCard)
    .cellSize(280, 176).gap(8)
    .onSelect(this::select)
    .onMove((movedId, anchorId, after) -> persistMove(movedId, anchorId, after));
grid.items(visibleEntries).selected(selectedId);
grid.scrollbar(SCROLLBAR_STYLE);
```

`cellSize` sets the minimum width and fixed height; the number of columns adapts
to the available logical width. `items` replaces the visible snapshot and cancels
any provisional drag. Equal snapshots keep their mounted widget; changed ones
replace it while transferring the focused descendant path for the same key.
Callers must include presentation data such as a full-library ordinal in the
snapshot if that data can change independently of the item payload.

Only visible rows plus overscan and the focused/dragged card stay attached.
Widget attachment and detachment are therefore the boundary for leases such as
thumbnail residency. Card factories must not spawn a simulation for every item
or depend on a widget remaining mounted after scrolling out of view. GPU resource
retirement must be deferred when the current GUI extraction may already contain
draw commands referencing it.

The grid consumes only a free-surface primary press that bubbled past the card's
children; focusable controls, including disabled ones, cannot start a card drag.
Four pixels of movement cross the drag threshold. Controls retain their own
press/drag/release sequence. The drag preview uses destination slots, a floating
card in a later GUI stratum, an empty insertion slot, exponential position lerp
and edge auto-scroll. Data is not reordered on pointer movement. A valid release
emits one `(movedId, anchorId, after)` callback; Escape, cancellation, data changes,
detachment and release outside the viewport discard it. `cancelDrag()` is also
available to the owning screen.

Wheel scrolling and scrollbars use the existing `ScrollView` implementation.
Content drag-to-scroll is intentionally unavailable because card dragging owns
that gesture. Arrow keys and Home/End navigate selection and reveal the focused
destination. `ReorderGridLayout` and `ReorderGridDrag` isolate layout, insertion
and gesture calculations for tests without Minecraft rendering. The grid never
interprets filtered-out items: persistence must resolve the supplied anchor IDs
against the complete authoritative collection.

`reorderable(false)` keeps virtualization, short-click selection and keyboard
navigation for catalogs without an editable order. Moving beyond the click
threshold cancels that gesture without floating cards or a move callback.

## Asset library

Code never contains texture paths. It uses `AssetKey` constants; files and draw
modes are defined in `assets/<namespace>/ui/assets.json`:

```json
{
  "icon/*": "sprite:icons/*",
  "panel/metal": "sprite:surface/metal/sprite",
  "screen/modal": { "texture": "gui/screen/modal", "size": [256, 256] },
  "frame/legacy": { "texture": "gui/legacy", "size": [64, 64], "uv": [0, 0, 32, 32], "mode": "nine_slice", "border": "4 6", "center": "tile" }
}
```

Path rules (one convention everywhere):

- `sprite:` = GUI-atlas sprite id, relative to `textures/gui/sprites/`, no
  `.png`. Stretch / tile / nine-slice comes from the sprite `.mcmeta`.
  Full paths are accepted and normalised.
- `texture:` = file relative to `textures/`, `.png` optional. Mode
  (`stretch`, `tile`, `nine_slice` + Insets-shorthand `border`, optional
  `center` / `edges` fill) is defined in the library.
- Keys and paths default to the file namespace; `ns:path` overrides.
- `key/*` patterns map families (icons, sockets); `*` in the path is replaced.
- Packs are layered bottom to top, so resource packs can override single keys.
- Unknown keys log once and draw a magenta placeholder. F3+T reloads.

Icons resolve by name through the `icon/*` pattern of their namespace:
`Button.icon(AssetKey.icon(MOD_ID, "common/close"), action)`,
`canvas.asset(AssetKey.icon(MOD_ID, "menu/graph"), rect, tint)`.

`Button.icon(...).text(label)` supports a centered icon and label with a 4 px gap.
Combined content is clipped to the button and long labels are ellipsized while
preserving component styles. `TextFit.fit(Font, Component, width)` exposes that
styled fitting for other widgets.

## Paint composition

`ImageFit.cover(area, imageWidth, imageHeight)` computes a centered, proportional
image rectangle that fully covers the target area, matching CSS
`object-fit: cover` and `object-position: center center`. Different aspect ratios
crop the overflow at opposite edges without a crop limit or empty bars. Draw the
full texture into the returned rectangle inside a paired `Canvas.pushClip(area)` /
`popClip()` block, restoring the clip in `finally`; the helper owns no rendering
state or texture resources.

`Paint` values are reusable and immutable. A saved asset can be tinted without
changing its shared definition:

```java
private static final Paint FRAME = Paint.asset(FRAME_KEY);

Paint cyanFrame = FRAME.color(ColorX.of("#7BFFFB")).inset(Insets.of(1));
Paint panel = Paint.fill(0x80000000).then(cyanFrame);
```

`color(ColorX)` multiplies RGBA, including the alpha channel. White is neutral;
repeated color decorators multiply their tints. `a.color(tint).then(b)` colors
only `a`, while `a.then(b).color(tint)` colors both layers. Geometry, layer order
and clipping are preserved. Solid fills use `Paint.fill(int)` / `Paint.fill(ColorX)`.
`Paint.asset(key, tint)` and `sprite(id, tint)` use the same decorator.

`Canvas.withColor` creates a color view of the same drawing surface, sharing its
graphics, clips and coordinate transforms. Tint is applied once at terminal
drawing methods, so delegated assets and nine-slices cannot multiply it twice.
Text keeps vanilla bidi/formatting order; explicit span and shadow RGB are tinted
as well, while text alpha is applied once through the normal font renderer.
No global shader color is changed, and an exception cannot leak color into the
parent canvas or sibling paints. Custom render states submitted through
`Canvas.submit` must use `canvas.tintColor(...)` for their color inputs; direct
`graphics()` calls and vanilla item rendering bypass the decorator. Shader
inputs that encode data rather than RGB (such as an HSV spectrum) require an
untinted canvas.

## Transformed canvases

- `Canvas.polyline(points, thickness, color)` draws sampled curves in fractional
  coordinates with mitred joints (no stair-stepping).
- `Canvas.pushTransform(dx, dy, scale)` draws contents in local units at any
  zoom; clips pushed before it stay in screen space.

Widgets inside a zoomable canvas (for example inline controls on graph nodes)
use `TransformContainer`, a normal attached widget subtree with logical layout
coordinates. `UiTransform` is shared by hit testing, pointer and scroll
dispatch, screen-space popup anchors and clipping. Pointer distances are
inverse-scaled; wheel steps and keyboard/text events retain their meaning.
`Canvas.pushCoordinateSpace` maps nested widget clips once into screen space;
the scissor is submitted with an identity pose because vanilla 26.3 otherwise
transforms it a second time. Popups stay ordinary root overlays and use
`Widget.screenBounds()` as their anchor; they remain usable outside the
transformed area.
