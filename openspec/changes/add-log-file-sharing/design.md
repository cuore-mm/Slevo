## Context

現在のログ基盤は `AppLogger` interface と `KermitAppLogger` によって、アプリコードから Kermit を直接参照しない構造になっている。`SlevoApplication` では Debug ビルド時のみ `platformLogWriter()` を設定し、Release ビルドでは writer を空にしているため、クラッシュやエラー発生後にユーザー端末から共有できるログファイルは存在しない。

一方で、アプリには既に FileProvider が設定されており、画像共有では `FileProvider.getUriForFile()` と `ACTION_SEND` を使う共有パターンが存在する。Issue 478 では、この既存のログ抽象化と共有基盤を活用し、「このアプリについて」画面からログを共有できる導線を追加する。

## Goals / Non-Goals

**Goals:**

- Kermit の writer としてファイル出力を追加し、既存の `AppLogger` 呼び出しからログファイルへ保存できるようにする。
- 未捕捉例外発生時にクラッシュ情報をログファイルへ追記する。
- 内部 files 領域のログファイルを FileProvider 経由で共有する。
- 「このアプリについて」画面に「ログを共有」項目を追加する。
- ログ未作成、空ログ、共有先なし、共有失敗時にアプリがクラッシュしないようにする。
- ログファイルの肥大化を防ぐ上限管理を行う。

**Non-Goals:**

- 外部クラッシュ解析 SDK や外部ログ収集サービスの導入は行わない。
- ログ閲覧 UI、ログ削除 UI、ログレベル設定 UI は追加しない。
- 既存の `AppLogger` 公開 API を Android 型または Kermit 型に依存させない。
- 既存の業務ロジック、通信処理、画面状態の挙動は変更しない。

## Decisions

### 内部 files 領域にログを保存する

ログ保存先は `context.filesDir/logs/app.log` とする。内部領域を使うことで外部ストレージ権限を不要にし、FileProvider の `<files-path>` で共有対象を限定できる。

代替案として cache 領域への保存も考えられるが、OS やアプリのキャッシュ削除で失われやすく、クラッシュ後の調査用途には不安定である。外部ストレージへの保存は権限や Android バージョン差分が増えるため採用しない。

### Kermit LogWriter としてファイル出力を追加する

既存の `AppLogger` は維持し、Kermit の writer 構成にファイル writer を追加する。これにより、Repository、DataSource、ViewModel など既存のログ呼び出し箇所を変更せずにファイル保存を有効化できる。

Debug ビルドでは `platformLogWriter()` とファイル writer の両方を設定する。Release ビルドでは障害解析に必要なログを残すためファイル writer を設定するが、出力内容は既存ログ呼び出しの範囲に限定し、機微情報を含む新規ログ追加は避ける。

### クラッシュ情報は既存 handler に委譲する前に記録する

`Thread.getDefaultUncaughtExceptionHandler()` で既存 handler を保持し、新しい handler で例外情報をログファイルへ追記してから既存 handler に委譲する。これにより Android 標準のクラッシュ処理や将来追加される handler の動作を妨げない。

### ログファイルにはサイズ上限を設ける

ログファイルが肥大化し続けないように、追記前または追記後にサイズを確認し、上限を超えた場合はローテーションまたは古い内容の破棄を行う。初期方針は実装を単純に保つため、単一世代の `app.log` と `app.log.old` のローテーション、または上限超過時の再作成とする。

### 共有は UI から分離した helper で扱う

`AboutScreen` はクリック callback を受け取るだけにし、Intent 作成、FileProvider URI 化、Toast 表示など Android 依存の処理は `ui/util` などの helper に分離する。これにより Composable は表示に集中し、既存の stateless な画面構造を維持できる。

### FileProvider でログディレクトリのみを公開する

`file_paths.xml` に `<files-path name="logs" path="logs/" />` を追加する。共有 Intent には `Intent.FLAG_GRANT_READ_URI_PERMISSION` と `ClipData` を設定し、共有先アプリに対象ログファイルの読み取り権限だけを一時付与する。

## Risks / Trade-offs

- ログに個人情報や投稿内容、URL などが含まれる可能性がある → 新規ログ追加時は機微情報を避け、今回の変更では既存ログの保存先追加を中心にする。
- Release ビルドでファイルログを有効にすると端末内に診断情報が残る → アプリ内部領域に保存し、ユーザー操作でのみ共有する。
- クラッシュ発生直後のファイル書き込みが失敗する可能性がある → handler 内では例外を握りつぶし、既存 handler への委譲を優先する。
- ログ共有対象ファイルが存在しない場合がある → 共有処理前に存在・サイズを確認し、ログがない旨を通知する。
- 共有先アプリがない場合がある → ActivityNotFoundException などを捕捉し、共有できない旨を通知する。
- ログローテーションの途中で共有される可能性がある → 共有時は現行ログファイルを対象とし、必要なら一時コピーを作成して安定した共有対象にする。

## Migration Plan

1. ファイルログ writer とログファイル管理処理を追加する。
2. `SlevoApplication` の Kermit 初期化にファイル writer を組み込む。
3. 未捕捉例外 handler を追加し、既存 handler へ委譲する。
4. FileProvider の paths にログディレクトリを追加する。
5. ログ共有 helper を追加する。
6. About 画面と navigation callback を接続する。
7. ログ保存、ログなし共有、共有 Intent、クラッシュ記録のテストを追加する。

ロールバックする場合は、`SlevoApplication` からファイル writer と未捕捉例外 handler の登録を外し、About 画面の共有項目と FileProvider path 追加を戻す。

## Open Questions

- Release ビルドで保存するログレベルを全レベルにするか、Error 以上に限定するか。
- ログローテーションは `app.log.old` の 1 世代保持で十分か、複数世代が必要か。
- 共有時に現行ログを直接共有するか、一時コピーを作成してから共有するか。
