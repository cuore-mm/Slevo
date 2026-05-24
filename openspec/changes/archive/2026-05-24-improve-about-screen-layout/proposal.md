## Why

現在の「このアプリについて」画面は、アプリ名・バージョン・外部リンク・ログ共有・OSS ライセンスが同じ ListItem 群として並んでおり、アプリ情報の視認性と設定画面群との見た目の一貫性が弱い状態です。
アプリアイコン、アプリ名、バージョンを画面上部にまとめて中央表示し、操作項目をカード化することで、アプリ情報と操作導線を分かりやすく分離します。

## What Changes

- 「このアプリについて」画面の上部に、アプリアイコン・アプリ名・バージョンを中央揃えで表示するヘッダー領域を追加する。
- GitHub、ログ共有、OSS ライセンスの 3 項目を `SettingsCardWithListItems` で表示する。
- GitHub、ログ共有、OSS ライセンスの各項目に leading icon を表示する。
- 既存の GitHub 遷移、ログ共有、OSS ライセンス画面遷移の動作は維持する。
- About 画面の Preview を新しいレイアウトに合わせて更新する。

## Capabilities

### New Capabilities
- `about-screen-layout`: 「このアプリについて」画面の表示構造、アプリ情報ヘッダー、操作項目カードの要件を扱う。

### Modified Capabilities

なし

## Impact

- `app/src/main/java/com/websarva/wings/android/slevo/ui/about/AboutScreen.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/settings/SettingsCardWithListItems.kt` および `ListItemSpec` / `listItemSpecOfBasic` の既存 API 利用
- `app/src/main/res/values/strings*.xml` の文言追加または既存文言利用
- `app/src/main/res/mipmap*` の既存ランチャーアイコン参照
