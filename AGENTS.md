# Repository Guidelines

## Project Structure & Modules
- Module: `app` (Android application).
- Source: `app/src/main/java/com/websarva/wings/android/slevo/...`
- UI: Jetpack Compose under `ui/*` with MVVM (`ViewModel`s) and Hilt DI (`di/*`).
- Data: Room + repositories and data sources under `data/*` (local/remote/models/utils).
- Tests: `app/src/test` (unit) and `app/src/androidTest` (instrumented).
- Assets/Resources: `app/src/main/res`; manifest in `app/src/main/AndroidManifest.xml`.

## Build, Test, and Dev Commands
- Build debug APK: `./gradlew :app:assembleDebug`
- Install on device/emulator: `./gradlew :app:installDebug`
- Run unit tests: `./gradlew :app:testDebugUnitTest`
- Run instrumented tests: `./gradlew :app:connectedDebugAndroidTest`
- Android Lint: `./gradlew :app:lintDebug`
- Resolve all dependencies (CI/cache warm-up): `./gradlew resolveAllDependencies`
- Example run via adb: `adb shell am start -n com.websarva.wings.android.slevo/.MainActivity`
- **コードを変更した場合は、必ずローカルでビルド（少なくとも `./gradlew :app:assembleDebug`）を実行し、ビルドが通るまで修正すること。**

## Coding Style & Naming
- Language: Kotlin with official style; use Android Studio formatter.
- Indentation: 4 spaces; 100–120 col soft wrap where helpful.
- Files: one top-level class/composable per file when reasonable.
- Naming: `PascalCase` for classes/composables, `camelCase` for methods/vars, `SCREAMING_SNAKE_CASE` for constants.
- Compose: prefer small, previewable composables; state in `ViewModel`; UI params are immutable.

## Testing Guidelines
- Frameworks: JUnit 4 (unit), AndroidX Test/Espresso/Compose Test (instrumented).
- Location: unit tests mirror package under `app/src/test/...`; instrumented under `app/src/androidTest/...`.
- Names: end with `Test` (e.g., `DatParserTest.kt`, `BoardRepositoryTest.kt`).
- Focus: pure logic (parsers in `data/util`, repositories) as unit tests; navigation/UI with Compose rule as instrumented tests.
- Run locally: use the Gradle tasks above; prefer headless unit tests for CI speed.

## Commits & Pull Requests
- Commits: follow Conventional Commits seen in history (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`).
- PRs must include: clear description, linked issues, screenshots/GIFs for UI changes, and test notes.
- Breaking changes: call out DB schema updates (Room) and provide migration; note DI changes (Hilt modules).

## Security & Config
- API keys: set `imgbb.api.key` in `local.properties` (not committed). Access via `BuildConfig.IMGBB_API_KEY`.
- Do not hardcode secrets; keep sample values/doc only.

## 設計・責務分離の指針
- ViewModel、UiState、Repository、Datasourceなどのファイルに分けて、責務の分離を意識してコーディングしてください。
- 特に、画面状態や表示に関するデータは必ずUiStateとして分離し、ViewModelで管理することを徹底してください。
