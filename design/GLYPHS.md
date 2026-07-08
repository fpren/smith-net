# Smith Net Glyph Registry (v2)

Glyphs are the icon language of the comm surface. They render ONLY in
JetBrains Mono, inside a fixed-width cell (1.3em web / 1.3.em-equivalent
Compose width), baseline-aligned with adjacent text. New iconography on the
comm surface must be added here first. Other surfaces may use Lucide line
icons (1.5px stroke, ink-muted default) where no glyph exists.

| Glyph | Meaning          | Allowed contexts                          | Never                              |
|-------|------------------|-------------------------------------------|------------------------------------|
| `●`   | online/present   | presence dots, avatar corner, counts       | as a bullet in body copy           |
| `○`   | offline/away     | presence dots, avatar corner               | as a decorative ring               |
| `[▣]` | photo attachment | message rows, attachment chips, notifs     | outside media contexts             |
| `[▶]` | voice/playable   | message rows, attachment chips, notifs     | as a generic "go" affordance       |
| `[≡]` | file attachment  | message rows, attachment chips, notifs     | as a menu/hamburger                |
| `>`   | composer prompt  | composer leading position only             | headings, breadcrumbs              |
| `←`   | back             | screen headers, sheet headers              | inline in sentences                |
| `↵`   | send             | composer trailing action                   | anywhere else                      |
| `▾`   | disclosure       | expanders, org switcher                    | sort indicators (use ops tables)   |

Presence colors come from tokens: statusOnline / inkMuted (offline).
Attention states use the amber `attention` token, never a new hex.
