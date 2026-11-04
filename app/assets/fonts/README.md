# Fonts

The app uses **Hind Siliguri** (Bangla; 400/500/600/700) and **Inter** (Latin + tabular numerals),
the same families as the web client (`docs/DESIGN_SYSTEM.md`). Both are SIL Open Font License 1.1.

Files to add here (static TTF, Bengali + Latin subset where available):

| File | Weight |
|---|---|
| `hind_siliguri_regular.ttf` | 400 |
| `hind_siliguri_medium.ttf` | 500 |
| `hind_siliguri_semibold.ttf` | 600 |
| `hind_siliguri_bold.ttf` | 700 |
| `inter_regular.ttf` | 400 |
| `inter_medium.ttf` | 500 |
| `inter_semibold.ttf` | 600 |

Compose resource names must be lowercase with underscores, as above.

## Wiring them in

Compose resources expect fonts in a folder named `font/`, so the build copies this folder into one:

1. In `shared/build.gradle.kts` (Compose mode), apply the Compose Multiplatform plugin (`org.jetbrains.compose`) and add

   ```kotlin
   val ssFonts = tasks.register<Sync>("ssFonts") {
       from(rootProject.layout.projectDirectory.dir("assets/fonts")) { include("*.ttf") }
       into(layout.buildDirectory.dir("ssResources/font"))
   }
   compose.resources {
       customDirectory("commonMain", ssFonts.map { it.destinationDir.parentFile.let(project.layout.projectDirectory::dir) })
   }
   ```
2. In `src/core/designsystem/Type.kt` replace the two `FontFamily.Default` values in `SsFonts` with
   `FontFamily(Font(Res.font.hind_siliguri_regular, FontWeight.Normal), …)`. Every text style already reads `SsFonts`.

Until then the platform default is used (Noto Sans Bengali on Android, the system Bangla font on iOS). Both
render conjuncts correctly, and the type scale and the 1.7 Bangla line height apply either way.
