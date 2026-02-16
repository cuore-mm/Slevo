## Why

AGP 9.0 では `android.newDsl=true` が既定となり、旧 DSL の `Project.android(configure: Action<BaseAppModuleExtension>)` と旧 Variant API への依存は将来の AGP 10.0 で削除予定です。現状のままではビルド設定の互換性が低下し、将来のアップデート時にビルド不能となるリスクがあるため、今のうちに新 DSL へ移行します。

## What Changes

- `android.newDsl=false` に依存した構成を廃止し、新 DSL（`ApplicationExtension`）前提の構成へ移行する。
- `applicationVariants` と `BaseVariantOutputImpl` を使う旧 Variant API の利用をやめ、`androidComponents` と公開 API で同等要件を満たす。
- 成果物取得は `androidComponents` の Artifacts API に一本化し、配布用ファイル名は Copy タスクで生成する。
- リリース APK 名は現行互換（`Slevo-<versionName>.apk`）を固定し、CI/検証向け出力では variant 名を含む命名で区別可能にする。

## Capabilities

### New Capabilities
- `agp-new-dsl-compatibility`: AGP 9 以降の新 DSL と公開 Variant API のみでビルド設定を維持する。

### Modified Capabilities
- なし

## Impact

- 対象: `gradle.properties`, `app/build.gradle.kts` を中心とした Gradle ビルド設定。
- 依存: Android Gradle Plugin の公開 DSL/Variant API への準拠。
- CI/配布: APK 名称規則や成果物出力パスの扱いに影響する可能性があるため、CI での生成物確認が必要。
