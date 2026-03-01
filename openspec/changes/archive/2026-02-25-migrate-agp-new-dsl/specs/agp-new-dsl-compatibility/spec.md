## ADDED Requirements

### Requirement: 新DSL互換ビルド設定
Gradle 設定は AGP 9 以降の新 DSL（`ApplicationExtension`）を前提として構成され、`android.newDsl=false` に依存してはならない。

#### Scenario: 新DSL既定でビルド設定が解決できる
- **WHEN** 開発者が AGP 9 以降で Gradle 同期または設定評価を実行する
- **THEN** 旧 DSL 互換モードを要求せずに Android 拡張設定が解決される

### Requirement: 旧Variant APIの排除
アプリモジュールのビルドスクリプトは `applicationVariants` および `BaseVariantOutputImpl` などの内部 API に依存してはならず、公開 Variant API で成果物操作を実現しなければならない。

#### Scenario: 旧Variant API参照が存在しない
- **WHEN** 開発者がアプリモジュールの Gradle スクリプトを確認する
- **THEN** `applicationVariants` と `com.android.build.gradle.internal.api.*` への参照が存在しない

### Requirement: リリース成果物要件の維持
新 DSL への移行後も、リリースビルド時の配布用 APK は Copy タスクで生成され、現行互換名 `Slevo-<versionName>.apk` に固定されなければならない。

#### Scenario: リリース成果物が期待形式で出力される
- **WHEN** 開発者がリリース APK 組み立てを実行する
- **THEN** Copy タスク経由で `Slevo-<versionName>.apk` が生成され、定義された出力先へ保存される

### Requirement: CI検証向け成果物の識別可能性
CI および検証用の成果物は variant 名を含む命名で出力され、同時に複数 variant を扱う場合でも識別できなければならない。

#### Scenario: CI/検証成果物にvariant名が含まれる
- **WHEN** 開発者または CI が `ci` などの検証用 variant の成果物を生成する
- **THEN** 出力 APK 名に variant 名が含まれ、リリース固定名と衝突しない

### Requirement: CIバリアント互換性
CI 用 buildType の versionCode 設定は移行後も有効でなければならず、実行環境の `GITHUB_RUN_NUMBER` を用いた上書きが継続されなければならない。

#### Scenario: CI実行番号がversionCodeへ反映される
- **WHEN** `ci` buildType のバリアントが `GITHUB_RUN_NUMBER` を含む環境で構成される
- **THEN** 対象バリアントの単一出力の versionCode に実行番号が設定される
