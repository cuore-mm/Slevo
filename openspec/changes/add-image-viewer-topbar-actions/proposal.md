## Why

画像ビューアのトップバーに主要操作がまとまっておらず、画像保存や追加操作への導線が不足している。閲覧中に素早く操作できる UI を提供するため、トップバーに保存ボタンとその他メニューを追加する。

## What Changes

- 画像ビューアのトップバーに「画像保存」アイコンボタンを追加する。
- トップバーに「その他」アイコンボタンを追加し、押下時に `DropdownMenu` で追加アクションを表示する。
- ドロップダウン表示中の開閉状態とアクション選択ハンドリングを、ビューア画面の状態遷移として定義する。

## Capabilities

### New Capabilities
- なし

### Modified Capabilities
- `image-viewer`: トップバーに保存ボタンとその他メニューを表示し、その他ボタン押下で追加アクションを選択できる要件を追加する。

## Impact

- 影響コード: `app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/*`
- 影響 UI: 画像ビューアのトップバー、その他メニュー表示
- 依存関係: Compose Material3 の `DropdownMenu` 利用
