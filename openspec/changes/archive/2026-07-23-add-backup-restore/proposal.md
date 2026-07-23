## Why

バックアップ作成機能により Slevo の利用データを ZIP として退避できるようになったが、機種変更、端末故障、アプリ再インストール後にそのバックアップをアプリへ戻す手段がまだない。Room DB と DataStore に分散したデータを、ユーザーが選択したバックアップ ZIP から安全に復元できる導線を追加する。

復元は現在のアプリ内データを上書きする破壊的操作であるため、復元前にバックアップ内容を検証し、作成日時・アプリ version・DB version・Cookie 含有有無・上書き警告を表示してから実行する。Cookie は認証情報に近いセンシティブデータであるため、バックアップに含まれている場合でも、復元時にユーザーが明示的に含める場合だけ復元する。

## What Changes

- 設定画面の既存「バックアップ作成」導線を「バックアップと復元」へ変更し、同じ画面からバックアップ作成と復元を実行できるようにする。
- ユーザーが Android のファイル選択 UI で選んだ ZIP バックアップを読み込み、`manifest.json` と内部固定パスを検証する。
- `backupFormatVersion = 1`、`backupMode = "full"`、現在の Room DB version と同一の `databaseVersion` を持つバックアップのみ復元対象にする。
- 復元はマージではなく全上書きとして扱い、Room DB、通常設定 DataStore、タブ選択 DataStore をバックアップ内容で置き換える。
- バックアップに `datastore/cookies.json` が含まれている場合でも、確認ダイアログでユーザーが「クッキーを復元する」を有効にした場合のみ Cookie DataStore を復元する。
- Room DB の置換は実行中の Hilt singleton `AppDatabase` を close して再利用せず、検証済みバックアップを pending restore として内部領域へ保存し、次回アプリ起動時に `AppDatabase` 生成前に実行する。
- 復元前確認ダイアログで、現在のデータが上書きされること、ZIP が未暗号化でセンシティブデータを含む可能性、Cookie 復元の注意を表示する。
- 復元準備中は重複実行を防ぎ、進捗ダイアログ、復元準備完了/失敗/無効なバックアップの Snackbar を表示し、復元適用にはアプリ再起動が必要であることを示す。
- 外部ストレージ権限、`MANAGE_EXTERNAL_STORAGE`、FileProvider は追加せず、Storage Access Framework の `OpenDocument` を使う。

## Capabilities

### New Capabilities

- `backup-restore`: 既存のバックアップ画面を「バックアップと復元」画面へ拡張し、同じ画面からバックアップ ZIP を選択して Room DB と DataStore を全上書き復元する機能。

### Modified Capabilities

- なし

## Impact

- 影響範囲:
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/settings/` 配下の設定画面と既存バックアップ画面の拡張
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/navigation/AppNavGraph.kt` と `SettingsRoute.kt` の既存 `SettingsBackup` route 表示内容
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/` 配下の ZIP reader、validator、restore repository orchestration、DB importer、DataStore 復元 mapper
  - `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/` 配下の Settings/Tabs/Cookie DataStore 反映 API
  - `app/src/main/java/com/websarva/wings/android/slevo/di/` または `data/backup/BackupModule.kt` の Hilt binding
- 既存 Room schema version は変更しない。
- バックアップ ZIP 形式 version 1 の読み込み契約を追加するが、出力形式自体は変更しない。
- 復元は destructive operation のため、実装時は自動テストに加えて実機/エミュレータで export → restore の手動確認が必要。
