## Context

現在の Gradle 設定は `android.newDsl=false` を前提とし、`applicationVariants` および `BaseVariantOutputImpl` による成果物操作を含んでいる。この構成は AGP 9 で互換維持モードとしては動作するが、既定の新 DSL（`android.newDsl=true`）および AGP 10 で削除予定の旧 API と整合しない。

本変更では、機能追加ではなくビルド基盤の将来互換性確保を目的に、公開 API のみで既存要件（ビルド成功、CI 向け versionCode 設定、リリース成果物命名・保存）を満たす。

## Goals / Non-Goals

**Goals:**
- 新 DSL を有効化した状態で deprecation 警告の原因となる旧 API 依存を除去する。
- 既存のビルド成果物要件（リリース APK 命名、`apk/` への保存、CI buildType の versionCode 適用）を維持する。
- AGP 9 以降で安定して動作する公開 DSL / Variant API に統一する。

**Non-Goals:**
- アプリ機能・UI・データ層の挙動変更。
- 依存ライブラリの大規模更新や Kotlin/Compose の再設計。
- 配布チャネルや署名方式の運用変更。

## Decisions

1. `android.newDsl=false` を廃止し、新 DSL 既定構成に合わせる。
   - 代替案: 一時的に旧設定を維持して警告のみ許容。
   - 採用理由: AGP 10 での削除が明示されており、先送りの効果が小さい。

2. `applicationVariants` / `BaseVariantOutputImpl` を使う内部 API 依存を廃止し、成果物取得は `androidComponents` の公開 Artifacts API に一本化する。
   - 代替案: internal API を継続利用して最小変更に留める。
   - 採用理由: internal API は将来互換性が低く、警告・破壊的変更の影響を受けやすい。

3. 配布用 APK 名は Artifacts API で取得した出力を入力にした Copy タスクで生成する。
   - 代替案: 出力生成時点で直接ファイル名を上書きする。
   - 採用理由: 生成物の取得と配布用命名を分離することで、Variant API 変更の影響を局所化できる。

4. 命名規則は用途別に固定する。
   - リリース: 現行互換名 `Slevo-<versionName>.apk` を固定する。
   - CI/検証: variant 名を含む命名を採用し、複数 buildType/variant の識別性を高める。

## Risks / Trade-offs

- [Risk] リリースと CI で命名規則を分けることで運用認識の齟齬が起きる可能性
  → Mitigation: リリース固定名と CI variant 名入り命名を仕様として明文化し、検証ステップで両方を確認する。

- [Risk] 成果物コピー処理のタイミング変更で `apk/` への出力が失敗する可能性
  → Mitigation: リリース組み立て後に出力先存在・件数を検証するチェックを追加する。

- [Risk] 新 DSL 有効化で既存設定の一部が無効化・非推奨化される可能性
  → Mitigation: `./gradlew :app:assembleRelease :app:testDebugUnitTest` を実行し、警告と失敗を移行時に解消する。

## Migration Plan

1. `gradle.properties` から `android.newDsl=false` を削除（または `true` 化）して新 DSL を既定化する。
2. `app/build.gradle.kts` の旧 Variant API 参照を公開 API ベースへ置換し、成果物取得を Artifacts API に一本化する。
3. Artifacts API の出力を入力とする Copy タスクを作成し、配布用ファイル名を生成する。
4. リリースは `Slevo-<versionName>.apk` 固定、CI/検証は variant 名入り命名で `apk/` へ出力する。
5. ビルド・ユニットテスト・生成物確認を実施し、互換性を検証する。

## Open Questions

- なし（成果物取得方式と命名規則を確定済み）。
