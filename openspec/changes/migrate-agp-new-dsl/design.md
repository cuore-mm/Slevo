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

2. `applicationVariants` / `BaseVariantOutputImpl` を使う内部 API 依存を廃止し、`androidComponents` と公開 Artifacts API へ移行する。
   - 代替案: internal API を継続利用して最小変更に留める。
   - 採用理由: internal API は将来互換性が低く、警告・破壊的変更の影響を受けやすい。

3. リリース成果物の命名・コピーは Variant 出力後処理タスクとして組み替える。
   - 代替案: 既存 `assembleProvider.doLast` と output キャストを継続。
   - 採用理由: 出力オブジェクトの実装型キャストを排除し、公開 API のみで保守可能にする。

## Risks / Trade-offs

- [Risk] AGP の API 差分により既存の APK ファイル名ルールが変わる可能性
  → Mitigation: 期待ファイル名を CI で検証し、差分発生時は生成ルールを明示的に固定する。

- [Risk] 成果物コピー処理のタイミング変更で `apk/` への出力が失敗する可能性
  → Mitigation: リリース組み立て後に出力先存在・件数を検証するチェックを追加する。

- [Risk] 新 DSL 有効化で既存設定の一部が無効化・非推奨化される可能性
  → Mitigation: `./gradlew :app:assembleRelease :app:testDebugUnitTest` を実行し、警告と失敗を移行時に解消する。

## Migration Plan

1. `gradle.properties` から `android.newDsl=false` を削除（または `true` 化）して新 DSL を既定化する。
2. `app/build.gradle.kts` の旧 Variant API 参照を公開 API ベースへ置換する。
3. リリース APK の命名と `apk/` への出力要件を新実装へ移す。
4. ビルド・ユニットテスト・CI 生成物確認を実施し、互換性を検証する。
5. 問題発生時は、成果物処理のみを段階的に切り戻せるようコミットを分離する。

## Open Questions

- AGP 9 系で採用する成果物コピー方式は、`androidComponents` の Artifacts API で統一するか、補助タスク併用で運用するか。
- リリース APK の命名規則を現行互換（`Slevo-<versionName>.apk`）で固定するか、variant 名を含む将来互換形式へ変更するか。
