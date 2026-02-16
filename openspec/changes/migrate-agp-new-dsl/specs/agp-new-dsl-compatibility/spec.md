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
新 DSL への移行後も、リリースビルド時に APK 命名規則と成果物出力手順が維持されなければならない。

#### Scenario: リリース成果物が期待形式で出力される
- **WHEN** 開発者がリリース APK 組み立てを実行する
- **THEN** 期待する命名規則の APK が生成され、定義された出力先へ保存される

### Requirement: CIバリアント互換性
CI 用 buildType の versionCode 設定は移行後も有効でなければならず、実行環境の `GITHUB_RUN_NUMBER` を用いた上書きが継続されなければならない。

#### Scenario: CI実行番号がversionCodeへ反映される
- **WHEN** `ci` buildType のバリアントが `GITHUB_RUN_NUMBER` を含む環境で構成される
- **THEN** 対象バリアントの単一出力の versionCode に実行番号が設定される
