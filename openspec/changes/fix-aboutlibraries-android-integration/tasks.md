## 1. AboutLibraries構成の公式準拠化

- [ ] 1.1 ルートGradleとアプリGradleのAboutLibrariesプラグイン定義を`com.mikepenz.aboutlibraries.plugin.android`へ統一する
- [ ] 1.2 `gradle/libs.versions.toml`のAboutLibraries関連バージョンを最新安定系列へ更新し、プラグインと依存の整合を取る
- [ ] 1.3 AboutLibrariesの既存拡張設定（`aboutlibs`配下のcollect設定）が更新後も有効であることを確認する

## 2. ライセンスデータ参照経路の固定

- [ ] 2.1 Open Source Licenses画面の読み込みを`rememberLibraries(R.raw.aboutlibraries)`へ戻し、入力を明示指定する
- [ ] 2.2 AGP 9のリソース縮小で`@raw/aboutlibraries`が除去されないようresource keepファイルを追加する
- [ ] 2.3 リリース構成で`@raw/aboutlibraries`がパッケージに含まれることを確認する

## 3. 検証

- [ ] 3.1 CI実行でAboutLibraries更新後もビルドとユニットテストが成功することを確認する
- [ ] 3.2 ライセンス画面起動時にクラッシュが再発しないことを確認する
- [ ] 3.3 変更手順と検証結果をOpenSpecタスクに反映し、完了条件を満たした状態でクローズ可能にする
