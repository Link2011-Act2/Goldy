# Demo appearance

Goldy Demo offers one UI style at a time: Material 3 Expressive, Nothing, or
standard Material 3. Material 3 Expressive is the initial default. The Style
dialog uses radio selection and scrolls vertically when space is limited.

Style, light/dark mode, dynamic color, and the custom-font example persist in
Demo-specific preferences. Styles are stored by name; an unknown saved value
falls back to Material 3 Expressive. Selecting a style does not reset the other
preferences or update-transfer state.

Nothing uses a monochrome palette. Dynamic color is disabled in that style,
while its saved value remains available when switching back to either Material
style. Elief inherits the host's MaterialTheme and UpdateCardStyle; Pyrite is a
Demo dependency only.

The same appearance settings apply to Showcase, Live Demo, Update Demo,
Notifications, and Style reference. Changing appearance does not start network
requests or installer actions. Horizontally scroll the settings row to reach
options on narrow screens.

`DemoAppearanceTest`, `DemoNavigationScreenTest`, `EliefLayoutTest`, and
`PyriteStyleTest` cover preference restoration, navigation, and layout. Use
the [verification tools](../verification/README.md) for local test commands.
