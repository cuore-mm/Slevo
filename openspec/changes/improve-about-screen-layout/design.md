## Context

「このアプリについて」画面は `Scaffold` と `LazyColumn` の中に Material3 `ListItem` を直接並べる構造になっている。
一方、設定画面群では `SettingsCardWithListItems` と `ListItemSpec` を使って、カード化された項目と leading icon を持つ一覧を構成している。
今回の変更では、静的なアプリ情報を画面上部のヘッダーとして切り出し、操作可能な項目を設定画面と同じカード表現へ寄せる。

## Goals / Non-Goals

**Goals:**
- About 画面の上部にアプリアイコン、アプリ名、バージョンを中央揃えで表示する。
- GitHub、ログ共有、OSS ライセンスを `SettingsCardWithListItems` でまとめて表示する。
- 3 つの操作項目に、それぞれ内容が分かる leading icon を表示する。
- 既存のクリック動作、ViewModel との接続、外部 URL 起動、ログ共有処理、OSS ライセンス画面遷移を維持する。
- Preview で新しいレイアウトを確認できるようにする。

**Non-Goals:**
- About 画面に新しい状態管理や `UiState` を追加しない。
- ログ共有機能、ライセンス表示機能、GitHub URL の仕様は変更しない。
- ランチャーアイコン画像そのものは変更しない。
- `SettingsCardWithListItems` の共通 API 変更は、既存 API で実現できない場合を除いて行わない。

## Decisions

### アプリ情報はカード項目ではなくヘッダーとして表示する

アプリアイコン、アプリ名、バージョンは操作項目ではなく画面の識別情報なので、`SettingsCardWithListItems` の中には含めず、`LazyColumn` の先頭に中央揃えのヘッダー領域として配置する。
これにより、アプリ情報と操作導線の役割を明確に分離する。

代替案として、アプリ名 ListItem の leading icon にランチャーアイコンを入れる方法もあるが、ユーザー要望の「アイコンとアプリ名とバージョンを中央に表示する」に合わないため採用しない。

### ランチャーアイコンは `Image` と `R.mipmap.ic_launcher` で表示する

アプリアイコンはフルカラー表示が前提のため、tint が適用される `Icon` ではなく `Image` を使う。
リソースは `R.mipmap.ic_launcher` を参照し、debug / ci / release などの flavor ごとのランチャーアイコン差し替えを Android のリソース解決に任せる。

### 操作項目は `SettingsCardWithListItems` と `listItemSpecOfBasic` で構成する

GitHub、ログ共有、OSS ライセンスは現在もクリック可能な ListItem として表現されているため、設定画面で使われている `SettingsCardWithListItems` に移行する。
各項目は `listItemSpecOfBasic` の `leadingContent` と `onClick` を使い、必要に応じて GitHub URL を supporting text として表示する。

### leading icon は Material Icons を優先する

各操作項目の leading icon は、既存の設定画面と同じ Material Icons の `Icon` を使う。
候補は GitHub に `Public` または `Code`、ログ共有に `Share`、OSS ライセンスに `Article` または `Description` とする。
プロジェクトに導入済みの icon artifact で利用可能なものを選び、追加依存は発生させない。

## Risks / Trade-offs

- ランチャーアイコンを大きく表示すると一部密度・adaptive icon の見え方が端末や Preview で差異を持つ可能性がある → `Image` に固定サイズを指定し、既存の `R.mipmap.ic_launcher` をそのまま利用する。
- `SettingsCardWithListItems` に移行すると余白や区切り線の見た目が現在の About 画面から変わる → 設定画面群と同じ `contentPadding` とカード表現に寄せることで一貫性を優先する。
- Material Icons の候補が現在の依存関係で利用できない可能性がある → 既に使われている icon set から選ぶか、利用可能な近い意味のアイコンへ差し替える。
