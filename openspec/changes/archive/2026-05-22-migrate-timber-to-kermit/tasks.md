## 1. 依存関係とログ基盤の追加

- [ ] 1.1 `gradle/libs.versions.toml` から Timber の version / library 定義を削除し、Kermit の version / library 定義を追加する
- [ ] 1.2 `app/build.gradle.kts` の `implementation(libs.timber)` を Kermit 依存へ置き換える
- [ ] 1.3 Android / Kermit 型に依存しない `AppLogger` interface を追加し、既存利用に必要な debug / info / error ログと tag / Throwable を扱える API にする
- [ ] 1.4 `AppLogger` を実装する `KermitAppLogger` を追加し、Kermit への message / tag / Throwable の委譲を実装する
- [ ] 1.5 Hilt module を追加し、`AppLogger` に `KermitAppLogger` を singleton として binding する

## 2. 初期化と呼び出し箇所の移行

- [ ] 2.1 `SlevoApplication` の `Timber.plant(Timber.DebugTree())` を Kermit の初期化処理へ置き換え、debug / release のログ出力方針を現在の挙動に合わせる
- [ ] 2.2 Repository の Timber 利用箇所に `AppLogger` を constructor injection し、`logger` 経由の呼び出しへ置き換える
- [ ] 2.3 Remote DataSource の Timber 利用箇所に `AppLogger` を constructor injection し、`logger` 経由の呼び出しへ置き換える
- [ ] 2.4 ViewModel の Timber 利用箇所に `AppLogger` を injection し、`logger` 経由の呼び出しへ置き換える
- [ ] 2.5 `NetworkModule` の OkHttp ログ出力を `AppLogger` 経由に変更し、`OkHttp` tag を保持する
- [ ] 2.6 Compose UI 内の不要な単発 Timber デバッグログを削除する

## 3. Timber 廃止の仕上げ

- [ ] 3.1 `import timber.log.Timber` と `Timber.` の直接呼び出しが残っていないことを確認し、残存箇所を削除する
- [ ] 3.2 Timber 依存が Gradle 定義と lock / catalog から残っていないことを確認する
- [ ] 3.3 Kermit API の引数順や tag / Throwable の扱いが specs の要件を満たしていることを確認する

## 4. テストと検証

- [ ] 4.1 constructor injection 変更で失敗する既存テストに no-op logger または mock logger を追加する
- [ ] 4.2 `./gradlew :app:assembleDebug` を実行し、アプリがビルドできることを確認する
- [ ] 4.3 `./gradlew :app:testDebugUnitTest` を実行し、単体テストが通ることを確認する
- [ ] 4.4 Issue #480 の受け入れ条件である「Timber 廃止」「Kermit 導入」「interface 経由利用」「現時点では KMP 化しない設計」を最終確認する
