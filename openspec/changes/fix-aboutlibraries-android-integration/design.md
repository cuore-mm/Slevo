## Context

現在のOpen Source Licenses画面は、AboutLibrariesの定義JSONを確実に参照できる前提で動作するが、ビルド設定側がAndroid向け自動生成フローに揃っておらず、実行時に`@raw/aboutlibraries`解決が不安定になっている。加えてAGP 9では最適化リソース縮小が既定で有効なため、生成済みJSONが縮小工程で除去されるリスクがある。

本変更は、AboutLibraries公式README/MIGRATIONで示されるv13系のプラグイン分割方針に従い、`13.2.1`固定の制約下ではメインプラグインの手動エクスポート機能をビルドへ組み込み、`aboutlibraries.json`生成を自動化する。

## Goals / Non-Goals

**Goals:**
- AboutLibrariesメタデータ生成をAndroidビルドに自動統合し、`@raw/aboutlibraries`が常に解決可能な状態を作る。
- Open Source Licenses画面の読み込み経路を`R.raw.aboutlibraries`明示指定で固定し、実行時の曖昧性を排除する。
- AboutLibraries関連のプラグイン/依存バージョンを`13.2.1`へ固定する。
- AGP 9のresource shrinking下でも`aboutlibraries`リソースを保持する。

**Non-Goals:**
- ライセンス画面のUIデザイン変更や情報表示項目の拡張。
- AboutLibraries以外の依存更新。
- OpenSpec `migrate-agp-new-dsl`の未完了タスク（release APK命名検証）の代替実施。

## Decisions

1. AboutLibrariesは`com.mikepenz.aboutlibraries.plugin`（13.2.1）を採用し、`exportLibraryDefinitions`を`preBuild`依存へ接続して生成を自動化する。
   - 理由: `13.2.1`の`.plugin.android`はAGP 9環境で`R.raw.aboutlibraries`生成に必要な連携が成立せず、コンパイル時にリソース未解決となるため。
   - 代替案: `.plugin.android`を継続利用する方式。
     - 却下理由: CIで`R.raw.aboutlibraries`未生成によるコンパイル失敗が再現した。

2. UI側は`rememberLibraries(R.raw.aboutlibraries)`でrawリソースを明示指定する。
   - 理由: Android実装の公式利用例と一致し、参照元を明確化できる。
   - 代替案: `rememberLibraries()`の自動検出任せ。
     - 却下理由: 実行時環境差分の影響を受けやすく、今回の障害再発を防ぎにくい。

3. AboutLibrariesバージョンは`13.2.1`へ固定し、出力先を`$buildDir/generated/aboutlibraries/res/raw/aboutlibraries.json`へ統一する。
   - 理由: v13以降のプラグイン分割仕様と整合し、AGP 9/新Variant API前提の改善を取り込める。
   - 代替案: 現行12.2.4を維持。
     - 却下理由: 公式の現行運用モデルとの差分が大きく、設定の意図が分かりにくい。

4. `@raw/aboutlibraries`をresource shrinkingから保護するkeep設定を追加する。
   - 理由: READMEにAGP 9の既定挙動として除去リスクが明記されている。
   - 代替案: shrink設定自体を無効化。
     - 却下理由: APK最適化方針を後退させるため採用しない。

## Risks / Trade-offs

- [Risk] AboutLibraries更新に伴いAPI/タスク名差分が出る → Mitigation: 公式README/MIGRATIONに合わせ、プラグインID・呼び出しAPI・生成物経路を一括で整合させる。
- [Risk] variantごとの生成タイミング差でCIのみ失敗する → Mitigation: CIで対象variantのassembleとライセンス画面表示までを確認し、生成と参照の実接続を検証する。
- [Trade-off] 自動生成方式はビルド時処理が増える → Mitigation: 手動運用を排し、再現性と保守性を優先する。

## Migration Plan

1. ルート/アプリのGradle plugin定義を`com.mikepenz.aboutlibraries.plugin`へ揃え、AboutLibraries関連バージョンを`13.2.1`へ固定する。
2. `exportLibraryDefinitions`の出力先を`$buildDir/generated/aboutlibraries/res/raw/aboutlibraries.json`に設定し、`preBuild`依存で自動生成する。
3. ライセンス画面の読み込みを`R.raw.aboutlibraries`明示指定へ戻す。
4. `tools:keep="@raw/aboutlibraries"`を含むkeepファイルを追加し、リソース縮小時の除去を防ぐ。
5. CIでassemble・unit test・ライセンス画面起動確認を実施し、クラッシュが再発しないことを確認する。
6. 問題発生時は、プラグイン更新差分をロールバックしつつ`R.raw`明示参照を維持して原因を切り分ける。

## Open Questions

- なし（ライセンス画面の実機確認は手動確認を採用）。
