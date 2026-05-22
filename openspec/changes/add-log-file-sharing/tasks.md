## 1. ログファイル基盤

- [x] 1.1 `core/log` 配下にログファイルの保存先、1MB の最大サイズ、`app.log` / `app.log.old` の 1 世代ローテーション方針を管理するクラスまたは utility を追加する。
- [x] 1.2 Kermit の LogWriter として動作し、時刻・レベル・tag・メッセージ・Throwable を `filesDir/logs/app.log` に追記するファイル writer を追加する。
- [x] 1.3 ログファイルがサイズ上限を超えた場合に、既存の `app.log.old` を削除し、`app.log` を `app.log.old` へ退避して新しい `app.log` への書き込みを継続できるようにする。
- [x] 1.4 ファイル書き込みやローテーション失敗時に、ログ処理がアプリ本体の処理をクラッシュさせないように例外処理を追加する。

## 2. アプリ起動時のログ設定とクラッシュ記録

- [x] 2.1 `SlevoApplication` の Kermit 初期化で、Debug ビルドは platform writer と全レベル保存の file writer、Release ビルドは ERROR 以上のみ保存する file writer を設定する。
- [x] 2.2 既存の未捕捉例外 handler を保持し、クラッシュ情報をログファイルに追記してから既存 handler へ委譲する handler を追加する。
- [x] 2.3 クラッシュ情報記録に失敗しても既存 handler への委譲が継続されるようにする。

## 3. FileProvider と共有処理

- [x] 3.1 `file_paths.xml` に cache 領域の `shared_logs/` ディレクトリを共有対象として追加する。
- [x] 3.2 保存済みログファイルの存在確認、空ファイル確認、共有用 cache ディレクトリへの一時コピー作成、FileProvider URI 作成、共有 Intent 作成を行う helper を追加する。
- [x] 3.3 共有 Intent に `ACTION_SEND`、一時コピーファイルの URI、MIME type、`FLAG_GRANT_READ_URI_PERMISSION`、必要な `ClipData` を設定する。
- [x] 3.4 古い共有用一時コピーを削除し、共有用 cache ディレクトリが肥大化しないようにする。
- [x] 3.5 ログなし、空ログ、一時コピー失敗、URI 作成失敗、共有先なしの場合にクラッシュせずユーザーへ通知する。

## 4. 「このアプリについて」画面の導線

- [x] 4.1 「ログを共有」および共有不可時のユーザー向け文言を string resource に追加する。
- [x] 4.2 `AboutScreen` に `onShareLogClick` callback を追加し、「ログを共有」ListItem を表示する。
- [x] 4.3 `AppNavGraph` で About 画面の `onShareLogClick` をログ共有 helper に接続する。
- [x] 4.4 必要に応じて `AboutScreen` の Preview を更新し、Preview 関数には KDoc を付けない。

## 5. テストと確認

- [x] 5.1 ファイル writer がログ内容と Throwable をファイルに保存することを単体テストで確認する。
- [x] 5.2 Debug ビルドでは全レベル、Release ビルドでは ERROR 以上のみ保存されることを単体テストで確認する。
- [x] 5.3 `app.log` / `app.log.old` の 1 世代ローテーション後もログ書き込みが継続することを単体テストで確認する。
- [x] 5.4 クラッシュ記録処理が既存 handler へ委譲し、書き込み失敗時も委譲を妨げないことをテストする。
- [x] 5.5 ログ共有 helper がログなし、空ログ、一時コピー作成、共有可能ログの各状態を正しく扱うことをテストする。
- [x] 5.6 AndroidManifest / FileProvider 設定、About 画面表示、共有 Intent の手動確認観点を整理する。
- [x] 5.7 実装後に CI または指定された build / unit test を実行し、成功を確認する。
