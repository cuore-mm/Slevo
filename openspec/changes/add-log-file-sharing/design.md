## Context

現在のログ基盤は `AppLogger` interface と `KermitAppLogger` によって、アプリコードから Kermit を直接参照しない構造になっている。`SlevoApplication` では Debug ビルド時のみ `platformLogWriter()` を設定し、Release ビルドでは writer を空にしているため、クラッシュやエラー発生後にユーザー端末から共有できるログファイルは存在しない。

一方で、アプリには既に FileProvider が設定されており、画像共有では `FileProvider.getUriForFile()` と `ACTION_SEND` を使う共有パターンが存在する。Issue 478 では、この既存のログ抽象化と共有基盤を活用し、「このアプリについて」画面からログを共有できる導線を追加する。

## Goals / Non-Goals

**Goals:**

- Kermit の writer としてファイル出力を追加し、既存の `AppLogger` 呼び出しからログファイルへ保存できるようにする。
- 未捕捉例外発生時にクラッシュ情報をログファイルへ追記する。
- 共有時点のログ内容を cache 領域へ一時コピーし、そのコピーを FileProvider 経由で共有する。
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

ログ保存先は `context.filesDir/logs/app.log` とする。内部領域を使うことで外部ストレージ権限を不要にし、アプリ管理下の診断情報として保持できる。

代替案として cache 領域への保存も考えられるが、OS やアプリのキャッシュ削除で失われやすく、クラッシュ後の調査用途には不安定である。外部ストレージへの保存は権限や Android バージョン差分が増えるため採用しない。

### Kermit LogWriter としてファイル出力を追加する

既存の `AppLogger` は維持し、Kermit の writer 構成にファイル writer を追加する。これにより、Repository、DataSource、ViewModel など既存のログ呼び出し箇所を変更せずにファイル保存を有効化できる。

Debug ビルドでは `platformLogWriter()` とファイル writer の両方を設定し、DEBUG / INFO / ERROR を保存する。Release ビルドでは障害解析に必要な最小限の情報に絞り、ERROR 以上と未捕捉例外のクラッシュ情報のみをファイルへ保存する。これにより、Release 端末内に詳細な操作ログや機微情報が残るリスクを抑える。

### クラッシュ情報は既存 handler に委譲する前に記録する

`Thread.getDefaultUncaughtExceptionHandler()` で既存 handler を保持し、新しい handler で例外情報をログファイルへ追記してから既存 handler に委譲する。これにより Android 標準のクラッシュ処理や将来追加される handler の動作を妨げない。

### ログファイルにはサイズ上限を設ける

ログファイルが肥大化し続けないように、追記前または追記後にサイズを確認し、上限を超えた場合は `app.log` を `app.log.old` へ退避して新しい `app.log` を作成する。初回実装では `app.log` と `app.log.old` の 1 世代保持とし、それぞれの目安上限は 1MB とする。

### 共有時は現行ログの一時コピーを作成する

共有対象には `filesDir/logs/app.log` を直接使わず、共有操作時点の内容を `cacheDir/shared_logs/slevo-log-YYYYMMDD-HHMMSS.log` のような一時ファイルへコピーしてから FileProvider URI を作成する。現行ログは共有中にも追記やローテーションが起こり得るため、共有時点の内容を固定したコピーを使うことで、共有先が読み取る内容を安定させる。

共有用コピーは cache 領域に置き、共有処理の前後または次回共有時に古いコピーを削除する。これにより、保存用ログと共有用ファイルの責務を分離し、FileProvider の公開範囲も共有用 cache ディレクトリに限定できる。

### About 画面のログ共有は ViewModel 経由で扱う

`AboutScreen` は stateless のままクリック callback を受け取り、実際のログ共有処理は `AboutViewModel` で行う。`AboutViewModel` は Hilt から `LogFileManager` を注入し、`LogShareUtil` を呼び出して共有処理を実行する。

これにより `MainActivity`、`AppScaffold`、`AppNavGraph` の各層で `LogFileManager` を引き回す必要がなくなり、About 画面に関係する処理を About 層へ集約できる。また将来「ログなし」「共有失敗」などの状態管理や one-shot event を追加しやすい。

### FileProvider で共有用 cache ディレクトリを公開する

`file_paths.xml` に `<cache-path name="shared_logs" path="shared_logs/" />` を追加する。共有 Intent には `Intent.FLAG_GRANT_READ_URI_PERMISSION` と `ClipData` を設定し、共有先アプリに一時コピーされたログファイルの読み取り権限だけを一時付与する。

### タイムスタンプフォーマットは thread-safe な `DateTimeFormatter` を使う

ログは複数スレッド（OkHttp、coroutine、main thread、crash handler）から同時に出力される可能性がある。`SimpleDateFormat` は mutable で thread-safe ではないため、タイムスタンプの競合でログ出力自体が不安定になるリスクがある。

そのため、`java.time.format.DateTimeFormatter` を使い、フォーマッタをプロパティとして安全に共有する。プロジェクトは `coreLibraryDesugaring` を有効にしており、minSdk 24 でも `java.time` API が利用できる。

## Risks / Trade-offs

- ログに個人情報や投稿内容、URL などが含まれる可能性がある → 新規ログ追加時は機微情報を避け、今回の変更では既存ログの保存先追加を中心にする。
- Release ビルドでファイルログを有効にすると端末内に診断情報が残る → Release では ERROR 以上とクラッシュ情報のみ保存し、ユーザー操作でのみ共有する。
- クラッシュ発生直後のファイル書き込みが失敗する可能性がある → handler 内では例外を握りつぶし、既存 handler への委譲を優先する。
- ログ共有対象ファイルが存在しない場合がある → 共有処理前に存在・サイズを確認し、ログがない旨を通知する。
- 共有先アプリがない場合がある → ActivityNotFoundException などを捕捉し、共有できない旨を通知する。
- ログローテーションの途中で共有される可能性がある → 共有時は現行ログを cache 領域へ一時コピーし、そのコピーを共有対象にする。

## Migration Plan

1. ファイルログ writer とログファイル管理処理を追加する。
2. `SlevoApplication` の Kermit 初期化にファイル writer を組み込む。
3. 未捕捉例外 handler を追加し、既存 handler へ委譲する。
4. FileProvider の paths に共有用 cache ディレクトリを追加する。
5. ログ共有 helper で現行ログの一時コピー作成と共有 Intent 作成を行う。
6. About 画面と navigation callback を接続する。
7. `AboutViewModel` を追加し、`LogFileManager` を注入してログ共有処理を担当する。
8. `MainActivity`、`AppScaffold`、`AppNavGraph` から `LogFileManager` の引き回しを削除し、`AboutViewModel` 経由で処理する。
7. ログ保存、ログなし共有、共有 Intent、クラッシュ記録のテストを追加する。

ロールバックする場合は、`SlevoApplication` からファイル writer と未捕捉例外 handler の登録を外し、About 画面の共有項目と FileProvider path 追加を戻す。

## Open Questions

- なし。初回実装では Release ビルドは ERROR 以上とクラッシュ情報のみ保存し、ローテーションは `app.log` / `app.log.old` の 1 世代保持、共有時は cache 領域の一時コピーを共有対象とする。
