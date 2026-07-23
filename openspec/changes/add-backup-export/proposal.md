## Why

機種変更、端末故障、アプリ再インストール時に、設定や閲覧データをユーザー自身が退避できる手段がない。Slevo 内の Room DB と DataStore に分散しているデータを、Android のファイルピッカー経由でデバイス上のバックアップファイルとして保存できるようにする。

バックアップには閲覧履歴、ブックマーク、投稿履歴、タブ状態、設定など個人に紐づく利用データが含まれる。ZIP は暗号化・パスワード保護しないため、ユーザーへ安全な保管を促す。クッキーは認証情報に近い特にセンシティブなデータであるため、通常のバックアップ対象とは分け、ユーザーが明示的に含める場合だけ出力する。

## What Changes

- 設定画面に「バックアップ作成」画面を追加する。
- ユーザーが `CreateDocument` で選択した保存先へ、`application/zip` と `.zip` 推奨ファイル名を使って単一 ZIP バックアップファイルを出力できるようにする。
- バックアップには Room DB 全体、通常設定 DataStore、タブ選択 DataStore、バックアップメタデータを含める。
- Room DB コピーの安全性は、別変更 `add-database-write-gate` で導入する `DatabaseWriteGate` を前提にする。
- クッキー DataStore は、バックアップ作成ボタン押下後の確認ダイアログでユーザーが「クッキーを含める」を有効にした場合のみバックアップに含める。
- DataStore はバックアップ内部では JSON として保存し、DataStore の物理ファイル形式には依存しない。
- Room DB は一貫性のある SQLite ファイルとして出力し、バックアップ ZIP に同梱する。
- 確認ダイアログで、標準バックアップにも個人データが含まれること、クッキーには認証情報が含まれる可能性があること、ZIP が未暗号化であることを説明する。
- 初期スコープではバックアップ作成を対象とし、復元はファイル形式と manifest で将来対応できるように設計する。後続の `add-backup-restore` では、この画面を「バックアップと復元」へ拡張して同じ画面から復元も実行できるようにする。

## Capabilities

### New Capabilities

- `backup-export`: 設定画面からアプリデータのバックアップファイルを作成し、クッキーを含めるかどうかを選択できる機能。

### Modified Capabilities

- なし

## Impact

- 影響範囲:
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/settings/` 配下の設定画面と新規バックアップ画面
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/navigation/AppNavGraph.kt` の設定系ルート
  - `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/` 配下の Room DB / DataStore 参照
  - `app/src/main/java/com/websarva/wings/android/slevo/data/repository/` 配下の新規バックアップ用 Repository
  - `app/src/main/java/com/websarva/wings/android/slevo/di/` 配下の Hilt binding
- Android の Storage Access Framework を使用し、外部ストレージ権限や FileProvider は追加しない。
- ZIP 書き込み、JSON 変換、DB エクスポートのための実装と単体テストを追加する。
- 既存の Room schema version は変更しない想定。
