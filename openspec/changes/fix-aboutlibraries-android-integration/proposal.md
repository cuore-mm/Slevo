## Why

Open Source Licenses画面でライセンス定義の生データを正しく解決できず、画面表示時にクラッシュする状態が発生している。AboutLibrariesの公式ガイドに沿ってAndroid向けの生成・読み込み経路を明確化し、AGP 9環境でも安定してライセンス情報を表示できる構成へ是正する必要がある。

## What Changes

- AboutLibrariesのGradleプラグイン構成を公式のAndroid向け運用に合わせ、ライブラリ定義JSONがAndroidビルドに自動連携されるようにする。
- Open Source Licenses画面の読み込み方式を`@raw/aboutlibraries`明示指定ベースへ統一し、生成物依存を明確化する。
- AboutLibraries関連の依存バージョンを最新安定版系列へ更新し、プラグインとUIライブラリの整合性を取る。
- AGP 9の最適化リソース縮小で`aboutlibraries.json`が除去されないよう、公式推奨のresource keep設定を追加する。
- CI/検証手順に、ライセンスデータ生成と画面起動に必要な確認項目を追加する。

## Capabilities

### New Capabilities
- `open-source-license-metadata`: AboutLibrariesの生成データをAndroidリソースとして安定供給し、ライセンス画面が常に参照可能な状態を保証する。

### Modified Capabilities
- なし

## Impact

- 影響ファイル: `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/java/com/websarva/wings/android/slevo/ui/about/OpenSourceLicenseScreen.kt`, `app/src/main/res/raw/*.xml`（keep設定）
- 影響領域: AboutLibrariesプラグイン適用方法、依存バージョン、リソース縮小設定、ライセンス画面のデータ読み込み経路
- 期待効果: ライセンス画面クラッシュの解消、AGP 9下での再発防止、構成の公式準拠
