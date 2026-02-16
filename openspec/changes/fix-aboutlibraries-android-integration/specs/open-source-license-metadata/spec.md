## ADDED Requirements

### Requirement: Androidビルドでライセンスメタデータを自動生成する
システムは、AboutLibrariesのAndroid向けプラグイン構成により、Androidビルド時に`aboutlibraries`メタデータを自動生成し、アプリが参照可能な`@raw/aboutlibraries`として提供しなければならない（MUST）。

#### Scenario: Android向けプラグイン構成でビルドする
- **WHEN** 開発者が対象モジュールをAndroid向けのAboutLibrariesプラグイン構成でビルドする
- **THEN** ライセンス定義データがビルド工程で生成され、実行時に`@raw/aboutlibraries`として解決できる

### Requirement: ライセンス画面はrawリソースを明示指定して読み込む
システムは、Open Source Licenses画面でライセンスデータを読み込む際、`R.raw.aboutlibraries`を明示指定して取得しなければならない（MUST）。

#### Scenario: 画面表示時にライセンスデータをロードする
- **WHEN** ユーザーがOpen Source Licenses画面を開く
- **THEN** 画面は`R.raw.aboutlibraries`を入力としてライセンス一覧を読み込み、JSON識別子未解決によるクラッシュを発生させない

### Requirement: AGP 9のリソース縮小でaboutlibrariesを保持する
システムは、最適化リソース縮小が有効なビルド設定でも`@raw/aboutlibraries`を保持するためのresource keep設定を持たなければならない（MUST）。

#### Scenario: releaseビルドでリソース縮小が有効な場合
- **WHEN** AGP 9環境でリソース縮小を伴うビルドを実行する
- **THEN** `@raw/aboutlibraries`は削除されず、ライセンス画面が必要とするデータを参照できる

### Requirement: AboutLibrariesのプラグインと依存バージョンを13.2.1へ固定する
システムは、AboutLibrariesのGradleプラグインとアプリ依存ライブラリのバージョンを`13.2.1`に固定し、相互互換性を維持しなければならない（MUST）。

#### Scenario: 依存解決時にバージョン整合を確認する
- **WHEN** 開発者がGradle同期またはCIビルドを実行する
- **THEN** AboutLibraries関連のプラグインと依存は互換のある同一系列として解決される
