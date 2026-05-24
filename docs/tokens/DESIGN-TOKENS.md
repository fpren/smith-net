# Smith Net — Design Tokens

**Format:** machine-readable JSON-style + adjacent table for human eyes.
**Version:** 1.0
**Source:** `EXTRACTED-PATTERNS.md` + actual `Theme.kt` + `ConsoleTheme.kt`
**Constraint:** light mode only. No dark variants.

This is the **canonical export** for engineering consumption (Step 11 PRDs may import these into a generated `Tokens.kt` if useful).

---

## 1. Color tokens

```json
{
  "color": {
    "background":          { "value": "#F6F8FA", "type": "color" },
    "surface":             { "value": "#FFFFFF", "type": "color" },
    "surfaceVariant":      { "value": "#EFF2F5", "type": "color" },
    "textPrimary":         { "value": "#1F2328", "type": "color" },
    "textMuted":           { "value": "#656D76", "type": "color" },
    "outline":             { "value": "#D0D7DE", "type": "color" },
    "outlineVariant":      { "value": "#EFF2F5", "type": "color" },
    "primary":             { "value": "#0969DA", "type": "color" },
    "onPrimary":           { "value": "#FFFFFF", "type": "color" },
    "primaryContainer":    { "value": "#DDF4FF", "type": "color" },
    "onPrimaryContainer":  { "value": "#0969DA", "type": "color" },
    "success":             { "value": "#1A7F37", "type": "color" },
    "successContainer":    { "value": "#DCFFE4", "type": "color" },
    "onSuccess":           { "value": "#FFFFFF", "type": "color" },
    "warning":             { "value": "#9A6700", "type": "color", "note": "amber for cap-approached emphasis; sparing use" },
    "error":               { "value": "#CF222E", "type": "color", "note": "TEXT-ONLY inside delete-confirmation dialog body; never a button fill" },
    "tertiary":            { "value": "#8250DF", "type": "color", "note": "very rare accent" },
    "statusGreen":         { "value": "#1A7F37", "type": "color", "alias": "success" },
    "statusGrey":          { "value": "#7D8590", "type": "color", "note": "for offline / disconnected status dots" }
  }
}
```

| Token | Hex | Use |
|---|---|---|
| `color.background` | `#F6F8FA` | page bg |
| `color.surface` | `#FFFFFF` | cards / rows |
| `color.surfaceVariant` | `#EFF2F5` | subtle distinction |
| `color.textPrimary` | `#1F2328` | body text |
| `color.textMuted` | `#656D76` | secondary text |
| `color.outline` | `#D0D7DE` | borders / separators |
| `color.outlineVariant` | `#EFF2F5` | softer borders |
| `color.primary` | `#0969DA` | primary actions / links |
| `color.onPrimary` | `#FFFFFF` | text on primary fill |
| `color.primaryContainer` | `#DDF4FF` | primary tint bg |
| `color.onPrimaryContainer` | `#0969DA` | text on primary tint |
| `color.success` | `#1A7F37` | success green |
| `color.successContainer` | `#DCFFE4` | success tint bg |
| `color.warning` | `#9A6700` | cap approaching (sparing) |
| `color.error` | `#CF222E` | destructive text only |
| `color.tertiary` | `#8250DF` | rare accent |
| `color.statusGreen` | `#1A7F37` | online / on |
| `color.statusGrey` | `#7D8590` | offline / off |

---

## 2. Typography tokens

```json
{
  "fontFamily": {
    "default": { "value": "FontFamily.Monospace", "type": "fontFamily" }
  },
  "fontWeight": {
    "regular": { "value": 400, "type": "fontWeight" },
    "semibold": { "value": 600, "type": "fontWeight" }
  },
  "fontSize": {
    "titleLarge":    { "value": 22, "type": "fontSize", "unit": "sp" },
    "titleMedium":   { "value": 16, "type": "fontSize", "unit": "sp" },
    "bodyLarge":     { "value": 16, "type": "fontSize", "unit": "sp" },
    "bodyMedium":    { "value": 14, "type": "fontSize", "unit": "sp" },
    "caption":       { "value": 12, "type": "fontSize", "unit": "sp" }
  },
  "lineHeight": {
    "titleLarge":    { "value": 28, "type": "lineHeight", "unit": "sp" },
    "titleMedium":   { "value": 24, "type": "lineHeight", "unit": "sp" },
    "bodyLarge":     { "value": 24, "type": "lineHeight", "unit": "sp" },
    "bodyMedium":    { "value": 20, "type": "lineHeight", "unit": "sp" },
    "caption":       { "value": 16, "type": "lineHeight", "unit": "sp" }
  },
  "type": {
    "title":       { "fontFamily": "{fontFamily.default}", "fontSize": "{fontSize.titleLarge}",  "fontWeight": "{fontWeight.semibold}", "lineHeight": "{lineHeight.titleLarge}",  "case": "uppercase" },
    "titleSmall":  { "fontFamily": "{fontFamily.default}", "fontSize": "{fontSize.titleMedium}", "fontWeight": "{fontWeight.semibold}", "lineHeight": "{lineHeight.titleMedium}", "case": "uppercase" },
    "body":        { "fontFamily": "{fontFamily.default}", "fontSize": "{fontSize.bodyMedium}",  "fontWeight": "{fontWeight.regular}",  "lineHeight": "{lineHeight.bodyMedium}",  "case": "preserve" },
    "bodyBold":    { "fontFamily": "{fontFamily.default}", "fontSize": "{fontSize.bodyMedium}",  "fontWeight": "{fontWeight.semibold}", "lineHeight": "{lineHeight.bodyMedium}",  "case": "preserve" },
    "bodyLarge":   { "fontFamily": "{fontFamily.default}", "fontSize": "{fontSize.bodyLarge}",   "fontWeight": "{fontWeight.regular}",  "lineHeight": "{lineHeight.bodyLarge}",   "case": "preserve" },
    "caption":     { "fontFamily": "{fontFamily.default}", "fontSize": "{fontSize.caption}",     "fontWeight": "{fontWeight.regular}",  "lineHeight": "{lineHeight.caption}",     "case": "preserve" },
    "captionBold": { "fontFamily": "{fontFamily.default}", "fontSize": "{fontSize.caption}",     "fontWeight": "{fontWeight.semibold}", "lineHeight": "{lineHeight.caption}",     "case": "uppercase" }
  }
}
```

| ConsoleTheme accessor | Composite |
|---|---|
| `ConsoleTheme.title` | `{type.title}` |
| `ConsoleTheme.body` | `{type.body}` |
| `ConsoleTheme.bodyBold` | `{type.bodyBold}` |
| `ConsoleTheme.caption` | `{type.caption}` |
| `ConsoleTheme.captionBold` | `{type.captionBold}` |

---

## 3. Spacing tokens

```json
{
  "spacing": {
    "base":  { "value": 2,  "type": "spacing", "unit": "dp" },
    "xxs":   { "value": 4,  "type": "spacing", "unit": "dp" },
    "xs":    { "value": 6,  "type": "spacing", "unit": "dp" },
    "sm":    { "value": 8,  "type": "spacing", "unit": "dp" },
    "md":    { "value": 10, "type": "spacing", "unit": "dp" },
    "lg":    { "value": 12, "type": "spacing", "unit": "dp" },
    "lgPlus":{ "value": 14, "type": "spacing", "unit": "dp" },
    "xl":    { "value": 16, "type": "spacing", "unit": "dp" },
    "xxl":   { "value": 24, "type": "spacing", "unit": "dp" },
    "xxxl":  { "value": 32, "type": "spacing", "unit": "dp" }
  },
  "padding": {
    "rowCompact":     { "value": 12, "type": "padding", "unit": "dp" },
    "rowStandard":    { "value": 14, "type": "padding", "unit": "dp" },
    "pageHorizontal": { "value": 16, "type": "padding", "unit": "dp" },
    "pageVertical":   { "value": 14, "type": "padding", "unit": "dp" },
    "pillHorizontal": { "value": 10, "type": "padding", "unit": "dp" },
    "pillVertical":   { "value":  6, "type": "padding", "unit": "dp" },
    "ctaVertical":    { "value": 14, "type": "padding", "unit": "dp" },
    "bannerHorizontal":{ "value": 12, "type": "padding", "unit": "dp" },
    "bannerVertical": { "value":  6, "type": "padding", "unit": "dp" }
  },
  "gap": {
    "itemGap":            { "value": 10, "type": "gap", "unit": "dp" },
    "sectionTopGap":      { "value": 16, "type": "gap", "unit": "dp" },
    "sectionBottomGap":   { "value": 12, "type": "gap", "unit": "dp" },
    "iconTextGap":        { "value": 14, "type": "gap", "unit": "dp" },
    "dotTextGap":         { "value":  8, "type": "gap", "unit": "dp" }
  }
}
```

---

## 4. Shape / radius tokens

```json
{
  "radius": {
    "none":    { "value":  0, "type": "borderRadius", "unit": "dp" },
    "input":   { "value":  4, "type": "borderRadius", "unit": "dp" },
    "button":  { "value":  6, "type": "borderRadius", "unit": "dp" },
    "circle":  { "value": -1, "type": "borderRadius", "note": "use CircleShape; semantic value" }
  },
  "shape": {
    "rectangle": { "value": "RectangleShape" },
    "input":     { "value": "RoundedCornerShape({radius.input})" },
    "button":    { "value": "RoundedCornerShape({radius.button})" },
    "circle":    { "value": "CircleShape" }
  }
}
```

---

## 5. Border / outline tokens

```json
{
  "border": {
    "thin":     { "value": 1, "type": "borderWidth", "unit": "dp" },
    "selected": { "value": 2, "type": "borderWidth", "unit": "dp" }
  },
  "stroke": {
    "default":   { "color": "{color.outline}",        "width": "{border.thin}" },
    "soft":      { "color": "{color.outlineVariant}", "width": "{border.thin}" },
    "selected":  { "color": "{color.primary}",        "width": "{border.selected}" },
    "disabled":  { "color": "{color.outlineVariant}", "width": "{border.thin}" }
  }
}
```

---

## 6. Status indicator tokens

```json
{
  "statusDot": {
    "size":      { "value": 8, "type": "size", "unit": "dp" },
    "shape":     { "value": "{shape.circle}" },
    "stateOn":   { "color": "{color.statusGreen}" },
    "stateOff":  { "color": "{color.statusGrey}" },
    "stateWarn": { "color": "{color.warning}" },
    "stateError":{ "color": "{color.error}" }
  }
}
```

---

## 7. Motion tokens

```json
{
  "motion": {
    "duration": {
      "instant":  { "value":   0, "type": "duration", "unit": "ms", "note": "default — most state changes snap" },
      "fast":     { "value": 150, "type": "duration", "unit": "ms" },
      "default":  { "value": 200, "type": "duration", "unit": "ms" },
      "slow":     { "value": 250, "type": "duration", "unit": "ms", "note": "MAX duration; never exceed for new UI" }
    },
    "easing": {
      "default":  { "value": "FastOutSlowInEasing" }
    },
    "transition": {
      "lockOverlayAppear":   { "duration": "{motion.duration.default}", "easing": "{motion.easing.default}", "kind": "Crossfade" },
      "tickRecompose":       { "duration": "{motion.duration.instant}", "kind": "snap" },
      "screenNavigation":    { "value": "platform-default" }
    },
    "reducedMotion": {
      "respectSystem": true,
      "fallbackDuration": "{motion.duration.instant}"
    }
  }
}
```

---

## 8. Layout tokens

```json
{
  "layout": {
    "minTapTarget":          { "value": 44, "type": "size", "unit": "dp" },
    "phoneSidePadding":      { "value": 16, "type": "padding", "unit": "dp" },
    "leftSidebarWidth":      { "value": 240, "type": "size", "unit": "dp" },
    "bottomToolbarHeight":   { "value": 56, "type": "size", "unit": "dp" },
    "trialBannerMinHeight":  { "value": 36, "type": "size", "unit": "dp", "note": "wraps to 2 lines when needed; no max" },
    "quickActionTilesPerRow":{ "value": 4, "type": "count" }
  }
}
```

---

## 9. Component-token mapping

```json
{
  "components": {
    "lockedFeatureOverlay": {
      "topCardBackground":     "{color.surface}",
      "topCardPadding":        "{padding.rowStandard}",
      "titleStyle":            "{type.captionBold}",
      "titleColor":            "{color.primary}",
      "bodyStyle":             "{type.body}",
      "bodyColor":             "{color.textPrimary}",
      "tierLabelStyle":        "{type.bodyBold}",
      "primaryCtaFill":        "{color.primary}",
      "primaryCtaText":        "{color.onPrimary}",
      "primaryCtaTextStyle":   "{type.captionBold}",
      "primaryCtaPadding":     "{padding.ctaVertical}",
      "primaryCtaShape":       "{shape.button}",
      "secondaryCtaStyle":     "{type.caption}",
      "secondaryCtaColor":     "{color.textMuted}",
      "dimAlpha":              0.4
    },
    "trialBanner": {
      "background":            "{color.surface}",
      "textStyle":             "{type.captionBold}",
      "textColor":             "{color.textPrimary}",
      "horizontalPadding":     "{padding.bannerHorizontal}",
      "verticalPadding":       "{padding.bannerVertical}",
      "bottomBorder":          "{stroke.default}"
    },
    "founderSeatsCounter": {
      "dotSize":               "{statusDot.size}",
      "dotShape":              "{statusDot.shape}",
      "dotColorAvailable":     "{color.statusGreen}",
      "dotColorExhausted":     "{color.statusGrey}",
      "labelStyle":            "{type.body}",
      "labelColorDefault":     "{color.textPrimary}",
      "labelColorAlmostGone":  "{color.primary}",
      "labelColorMuted":       "{color.textMuted}",
      "dotTextGap":            "{gap.dotTextGap}"
    },
    "tierPricingScreen": {
      "headerHeight":          "{layout.bottomToolbarHeight}",
      "sectionGap":            { "computed": "{gap.sectionTopGap} + 1 (separator) + {gap.sectionBottomGap}" },
      "currentTierTint":       "{color.surfaceVariant}",
      "anchorTableTopGap":     "{spacing.xxl}"
    },
    "subscriptionDetailScreen": {
      "rowPadding":            "{padding.rowStandard}",
      "destructiveDialogTextColor": "{color.error}",
      "destructiveButtonFill": "{color.surface}",
      "destructiveButtonText": "{color.textPrimary}",
      "destructiveButtonBorder": "{stroke.default}"
    },
    "entitlementLock": {
      "lockDotColor":          "{color.statusGrey}",
      "lockDotSize":           "{statusDot.size}",
      "rowBackground":         "{color.surface}",
      "rowPadding":            "{padding.rowStandard}",
      "ctaTextStyle":          "{type.caption}",
      "ctaTextColor":          "{color.primary}"
    },
    "pdfSendCounterFooter": {
      "textStyle":             "{type.caption}",
      "textColor":             "{color.textMuted}",
      "textColorEmphasized":   "{color.textPrimary}",
      "topPadding":            "{spacing.lg}"
    }
  }
}
```

---

## 10. Glyph tokens (Unicode-only icon set)

```json
{
  "glyph": {
    "back":            { "value": "←",     "unicode": "U+2190" },
    "forward":         { "value": ">",     "unicode": "U+003E" },
    "filledDot":       { "value": "●",     "unicode": "U+25CF" },
    "emptyDot":        { "value": "○",     "unicode": "U+25CB" },
    "toggleOn":        { "value": "((●))", "composite": true },
    "toggleOff":       { "value": "((○))", "composite": true },
    "star":            { "value": "★",     "unicode": "U+2605" },
    "tilde":           { "value": "~",     "unicode": "U+007E" },
    "plus":            { "value": "+",     "unicode": "U+002B" },
    "middot":          { "value": "·",     "unicode": "U+00B7" }
  }
}
```

---

## 11. Theme constraint tokens

```json
{
  "theme": {
    "mode":                "light-only",
    "respectSystemDarkMode": false,
    "statusBarColor":      "{color.background}",
    "lightStatusBarIcons": true,
    "fontScaleMin":        0.85,
    "fontScaleMax":        1.30
  }
}
```

---

## 12. Accessibility tokens

```json
{
  "a11y": {
    "minTapTargetDp":       "{layout.minTapTarget}",
    "minContrastBody":      4.5,
    "minContrastLargeText": 3.0,
    "minContrastUI":        3.0,
    "supportTalkBack":      true,
    "supportDynamicType":   true,
    "supportReducedMotion": true,
    "colorAloneNeverConveysMeaning": true
  }
}
```

---

## 13. Forbidden-tokens registry

The following are explicitly NOT defined and MUST NOT be added without a design-system version bump:

```json
{
  "forbidden": {
    "darkMode":                "no dark color scheme; theme.mode=light-only",
    "shadowElevation":         "no Modifier.shadow(...) or graphicsLayer shadow",
    "gradient":                "no Brush.linearGradient or radialGradient on surfaces",
    "customFonts":             "no font families besides FontFamily.Monospace",
    "italics":                 "no italic font styles",
    "newColors":               "do not introduce hex outside palette in §1",
    "longAnimations":          "no animation > 250ms",
    "springAnimations":        "no spring physics; FastOutSlowInEasing only",
    "materialButtons":         "no androidx.compose.material3.Button family",
    "materialDialogs":         "no AlertDialog",
    "materialSnackbar":        "no Snackbar; use Toast"
  }
}
```

---

## 14. Token consumption (for Step 11 PRDs)

A future `Tokens.kt` could be auto-generated from this file:

```kotlin
// Auto-generated; do not edit.
package com.guildofsmiths.trademesh.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object DesignTokens {
    object Color {
        val background       = Color(0xFFF6F8FA)
        val surface          = Color(0xFFFFFFFF)
        val surfaceVariant   = Color(0xFFEFF2F5)
        val textPrimary      = Color(0xFF1F2328)
        val textMuted        = Color(0xFF656D76)
        val outline          = Color(0xFFD0D7DE)
        val outlineVariant   = Color(0xFFEFF2F5)
        val primary          = Color(0xFF0969DA)
        val onPrimary        = Color(0xFFFFFFFF)
        val primaryContainer = Color(0xFFDDF4FF)
        val success          = Color(0xFF1A7F37)
        val successContainer = Color(0xFFDCFFE4)
        val warning          = Color(0xFF9A6700)
        val error            = Color(0xFFCF222E)
        val tertiary         = Color(0xFF8250DF)
        val statusGreen      = success
        val statusGrey       = Color(0xFF7D8590)
    }
    object Spacing {
        val xxs    =  4.dp
        val xs     =  6.dp
        val sm     =  8.dp
        val md     = 10.dp
        val lg     = 12.dp
        val lgPlus = 14.dp
        val xl     = 16.dp
        val xxl    = 24.dp
        val xxxl   = 32.dp
    }
    // … etc.
}
```

This is **not** to be hand-maintained — it'd be regenerated from this token doc whenever palette/spacing changes. Step 11 PRD F-tokens-codegen tracks this.

---

## 15. Versioning

- **Tokens v1.0** — this file. Locked at start of Step 6.
- Any token change requires:
  1. PR updating this file
  2. PR updating `DESIGN-SYSTEM.md` (prose explanation)
  3. PR updating `EXTRACTED-PATTERNS.md` if new pattern observed
  4. PR regenerating `Tokens.kt` (when codegen exists)
  5. PR bumping `ConsoleTheme.kt` doc-comment version

The 5-step requirement is intentional friction. Design tokens are not safe to change casually.
