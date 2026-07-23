## ADDED Requirements

### Requirement: バックアップと復元画面への導線
システムは設定画面から既存のバックアップ画面へ遷移し、その同じ画面上でバックアップ作成と復元を実行できる導線を提供しなければならない（MUST）。

#### Scenario: 設定画面からバックアップと復元画面を開く
- **WHEN** ユーザーが設定画面の「バックアップと復元」項目を選択する
- **THEN** システムはバックアップ作成 action と復元 action を含むバックアップと復元画面を表示する

#### Scenario: 同じ画面からバックアップ作成を開始する
- **WHEN** ユーザーがバックアップと復元画面の「バックアップ作成」action を選択する
- **THEN** システムは既存のバックアップ作成確認フローを開始する

#### Scenario: 同じ画面から復元ファイル選択を開始する
- **WHEN** ユーザーがバックアップと復元画面の「バックアップから復元」action を選択する
- **THEN** システムは Android のファイル選択 UI を表示する

### Requirement: バックアップファイルの選択
システムは Storage Access Framework のファイル選択 UI でユーザーが選択したバックアップ ZIP を読み込まなければならない（MUST）。

#### Scenario: ファイル選択 UI でバックアップを選択する
- **WHEN** ユーザーが Android のファイル選択 UI でバックアップファイルを選択する
- **THEN** システムは返却された URI からバックアップ内容の preview を読み込む

#### Scenario: ファイル選択をキャンセルする
- **WHEN** ユーザーが Android のファイル選択 UI をキャンセルする
- **THEN** システムは復元処理を開始せず、エラー表示を行わない

#### Scenario: provider 側表示名に依存しない
- **WHEN** Android のファイル選択 UI が URI を返す
- **THEN** システムは返却された URI の provider 側表示名や `.zip` 拡張子だけで復元可否を判定しない

### Requirement: バックアップ形式の検証
システムは復元前に ZIP 内部構造、manifest、必須ファイル、DB version を検証しなければならない（MUST）。

#### Scenario: version 1 full backup を受け付ける
- **WHEN** 選択された ZIP の `manifest.json` が `backupFormatVersion = 1`、`backupMode = "full"`、現在の Room DB version と同じ `databaseVersion` を持つ
- **THEN** システムは復元候補として preview を表示できる

#### Scenario: 未対応 backupFormatVersion を拒否する
- **WHEN** `manifest.json` の `backupFormatVersion` が `1` ではない
- **THEN** システムは復元を開始せず、無効または未対応のバックアップとして通知する

#### Scenario: 未対応 backupMode を拒否する
- **WHEN** `manifest.json` の `backupMode` が `"full"` ではない
- **THEN** システムは復元を開始せず、無効または未対応のバックアップとして通知する

#### Scenario: DB version 不一致を拒否する
- **WHEN** `manifest.json` の `databaseVersion` が現在の Room DB version と一致しない
- **THEN** システムは復元を開始せず、現在のアプリでは復元できないバックアップとして通知する

#### Scenario: 必須 entry 不足を拒否する
- **WHEN** ZIP に `manifest.json`、`database/slevo.db`、`datastore/settings.json`、または `datastore/tabs.json` が存在しない
- **THEN** システムは復元を開始せず、無効なバックアップとして通知する

#### Scenario: Cookie manifest と entry の不一致を拒否する
- **WHEN** `manifest.included.cookies` と `datastore/cookies.json` の有無が一致しない
- **THEN** システムは復元を開始せず、無効なバックアップとして通知する

#### Scenario: 不正 path を拒否する
- **WHEN** ZIP に `../`、絶対パス、空 entry 名、または固定パス以外の entry が含まれる
- **THEN** システムは復元を開始せず、無効なバックアップとして通知する

#### Scenario: 既知 directory entry を無視する
- **WHEN** ZIP に `database/` または `datastore/` の directory entry が含まれる
- **THEN** システムはそれらを directory marker として無視し、必須 file entry の存在判定には使わない

#### Scenario: 未知 directory entry を拒否する
- **WHEN** ZIP に `database/` と `datastore/` 以外の directory entry が含まれる
- **THEN** システムは復元を開始せず、無効なバックアップとして通知する

#### Scenario: commit 時に再検証する
- **WHEN** preview 成功後にユーザーが復元を確定する
- **THEN** システムは preview 結果だけを信頼せず、pending restore 作成前に同じ URI の ZIP、manifest、DB、DataStore JSON を再読み込みして再検証する

#### Scenario: commit 時再検証に失敗する
- **WHEN** preview 成功後の commit 時再検証で ZIP 内容、manifest、DB、または DataStore JSON が無効になっている
- **THEN** システムは pending restore を作成せず、無効なバックアップとして通知する

#### Scenario: 不正な JSON 値を拒否する
- **WHEN** DataStore JSON が malformed、必須 field 不足、未知 enum、null 不許可 field の null、または許可範囲外の値を含む
- **THEN** システムは pending restore を作成せず、無効なバックアップとして通知する

#### Scenario: 正の有限 scale 値を受け付ける
- **WHEN** `datastore/settings.json` の scale または lineHeight が正の有限値である
- **THEN** システムは v1 の数値 validation としてはその値を受け付ける

### Requirement: 復元前確認
システムはバックアップ preview の検証に成功した後、復元を開始する前に確認ダイアログを表示しなければならない（MUST）。

#### Scenario: preview 情報を表示する
- **WHEN** システムが復元前確認ダイアログを表示する
- **THEN** システムはバックアップの作成日時、作成元アプリ version、DB version、Cookie 含有有無を表示する

#### Scenario: 上書き警告を表示する
- **WHEN** システムが復元前確認ダイアログを表示する
- **THEN** システムは現在のアプリ内データがバックアップ内容で上書きされることを表示する

#### Scenario: 未暗号化と個人データの注意を表示する
- **WHEN** システムが復元前確認ダイアログを表示する
- **THEN** システムはバックアップ ZIP が未暗号化で、履歴、ブックマーク、投稿履歴、タブ状態、設定など個人に紐づくデータを含む可能性があることを表示する

#### Scenario: 確認ダイアログをキャンセルする
- **WHEN** ユーザーが復元前確認ダイアログをキャンセルする
- **THEN** システムは DB または DataStore を変更せず、復元処理を開始しない

### Requirement: Cookie 復元の明示選択
システムはバックアップに Cookie が含まれる場合でも、ユーザーが確認ダイアログで明示的に選択した場合のみ Cookie を復元しなければならない（MUST）。

#### Scenario: Cookie 復元選択の初期状態
- **WHEN** Cookie を含むバックアップの復元前確認ダイアログを表示する
- **THEN** システムは「クッキーを復元する」を未選択状態として表示する

#### Scenario: Cookie を復元する
- **WHEN** ユーザーが「クッキーを復元する」を有効にして復元を確定する
- **THEN** システムは `datastore/cookies.json` を pending restore の復元対象として staging し、Cookie DataStore は次回起動時の pending restore 適用時に反映する

#### Scenario: Cookie を復元しない
- **WHEN** ユーザーが「クッキーを復元する」を無効のまま復元を確定する
- **THEN** システムは `datastore/cookies.json` がバックアップに含まれていても pending restore の復元対象に含めず、Cookie DataStore を変更しない

#### Scenario: Cookie を含まないバックアップの確認
- **WHEN** Cookie を含まないバックアップの復元前確認ダイアログを表示する
- **THEN** システムは Cookie 復元 checkbox を表示しない、または無効状態として表示する

#### Scenario: Cookie の注意を表示する
- **WHEN** Cookie を含むバックアップの復元前確認ダイアログを表示する
- **THEN** システムは Cookie に認証情報が含まれる可能性があることを表示する

### Requirement: pending restore の準備
システムはバックアップ内の `database/slevo.db` と DataStore JSON を検証した後、次回起動時に適用する pending restore として内部領域へ保存しなければならない（MUST）。

#### Scenario: 復元対象 DB の整合性を検証する
- **WHEN** システムが `database/slevo.db` を pending restore として保存しようとする
- **THEN** システムは復元対象 DB を読み取り専用で開き、`PRAGMA integrity_check` が `ok` を返すことを確認する

#### Scenario: DB schema compatibility を検証する
- **WHEN** システムが `database/slevo.db` を pending restore として保存しようとする
- **THEN** システムは `PRAGMA user_version = 9`、`room_master_table` の `id = 42` / `identity_hash = "f87f9edff16faf278567dbb60497a466"`、および design.md section 8 に列挙した 20 個の必須 application table が存在することを確認する

#### Scenario: 整合性検証に失敗した DB を拒否する
- **WHEN** 復元対象 DB を開けない、または `PRAGMA integrity_check` が `ok` 以外を返す
- **THEN** システムは pending restore を作成せず、復元を失敗または無効なバックアップとして通知する

#### Scenario: schema validation に失敗した DB を拒否する
- **WHEN** 復元対象 DB の `user_version`、Room identity hash、または必須 application table が現在 schema と一致しない
- **THEN** システムは pending restore を作成せず、無効なバックアップとして通知する

#### Scenario: pending marker を最後に作成する
- **WHEN** システムが pending restore を準備する
- **THEN** システムは DB と DataStore JSON の staging が完了した後に pending marker を作成する

#### Scenario: 既存 prepared pending がある場合は新規準備を拒否する
- **WHEN** `prepared` 状態の pending restore が存在する状態でユーザーが別の復元を確定する
- **THEN** システムは新しい pending restore を作成せず、既存の復元準備を再起動で適用する必要があることを通知する

#### Scenario: 既存 failed pending がある場合は cleanup 後に新規準備する
- **WHEN** `failed` 状態の pending restore が存在する状態でユーザーが別の復元を確定する
- **THEN** システムは既存 pending directory、rollback backup、result file を cleanup できた場合のみ新しい pending restore を作成する

#### Scenario: 既存 applying または db-swapped pending がある場合は新規準備を拒否する
- **WHEN** `applying` または `db-swapped` 状態の pending restore が存在する状態でユーザーが別の復元を確定する
- **THEN** システムは新しい pending restore を作成せず、次回起動時の recovery を優先する

#### Scenario: 実行中の AppDatabase を close しない
- **WHEN** システムが pending restore を準備する
- **THEN** システムは Hilt singleton の既存 `AppDatabase` を close せず、live DB ファイルを即時置換しない

#### Scenario: 復元準備完了を通知する
- **WHEN** pending restore の作成が完了する
- **THEN** システムはアプリ再起動後に復元が適用されることをユーザーへ通知する

### Requirement: 起動時の pending restore 適用
システムは pending restore が存在する場合、Hilt による `AppDatabase` 生成前に DB ファイルをバックアップ DB で全上書きしなければならない（MUST）。

#### Scenario: rollback backup を作成する
- **WHEN** アプリ起動時に pending restore の DB を live DB path へ置換し、live DB main file が存在する
- **THEN** システムは置換前の live DB main file と対応する `-wal` / `-shm` を rollback backup として保存する

#### Scenario: main DB rollback を作成できない場合は置換しない
- **WHEN** live DB main file が存在するが rollback backup へコピーできない
- **THEN** システムは live DB を置換せず、pending restore を `failed` として記録する

#### Scenario: WAL または SHM のコピー失敗を記録する
- **WHEN** live DB の `-wal` または `-shm` が存在するが rollback backup へコピーできない
- **THEN** システムは詳細ログへ記録し、main DB rollback が存在する場合は復元適用を続行できる

#### Scenario: live DB が未作成でも復元を適用する
- **WHEN** fresh install などで live DB main file が存在しない状態で pending restore を適用する
- **THEN** システムは rollback source なしとして扱い、live DB 不在だけを理由に復元適用を失敗にしない

#### Scenario: WAL と SHM が存在しない場合を許容する
- **WHEN** live DB path に対応する `-wal` または `-shm` が存在しない状態で pending restore を適用する
- **THEN** システムはそれらの不在を正常として扱う

#### Scenario: AppDatabase 生成前に DB を置換する
- **WHEN** アプリ起動時に pending restore marker が存在する
- **THEN** システムは Hilt が `AppDatabase` を生成する前に pending restore の DB を live DB path へ置換する

#### Scenario: pending restore 適用を同期的に完了する
- **WHEN** アプリ起動時に pending restore marker が存在する
- **THEN** システムは DB 置換判断と必要な pending restore 適用を完了するまで通常の `AppDatabase` 生成へ進まない

#### Scenario: 起動時復元 applier は state machine orchestration に集中する
- **WHEN** システムが起動時 pending restore を適用する
- **THEN** `PendingRestoreApplier` は marker status に基づく分岐と collaborator 呼び出し順の制御を担当し、marker/result file I/O、DB file 操作、DataStore JSON 反映の詳細処理を専用 collaborator へ委譲する

#### Scenario: 起動時復元 collaborator は DB/Hilt に依存しない
- **WHEN** システムが marker/result file I/O、DB file swap、または DataStore JSON 反映を行う
- **THEN** それぞれの collaborator は `AppDatabase`、DAO、Repository、Hilt EntryPoint に依存せず、`AppDatabase` を生成または close しない

#### Scenario: DB swapper が temp file cleanup を担当する
- **WHEN** DB file 置換で temp file 作成後に rename または replace が失敗する
- **THEN** システムは DB swapper の責務として temp file を best-effort で削除し、pending restore を失敗として記録する

#### Scenario: 起動時復元例外で通常起動を妨げない
- **WHEN** `PendingRestoreApplier.runIfNeeded()` の実行中に想定外例外が発生する
- **THEN** システムは例外を外へ投げず、可能な場合は pending restore を `failed` として記録し、通常のアプリ初期化を継続する

#### Scenario: 起動時復元の重い I/O を IO dispatcher で実行する
- **WHEN** システムが marker/result file、DB file、WAL/SHM、SQLite、DataStore JSON、または DataStore edit を扱う
- **THEN** システムはそれらの処理を `Dispatchers.IO` 相当の I/O dispatcher 上で実行し、main thread 上で直接重い I/O を行わない

#### Scenario: 起動時復元の例外記録 I/O も IO dispatcher で実行する
- **WHEN** 起動時復元中に想定外例外が発生し、システムが marker または result file を更新する
- **THEN** システムは例外記録の file I/O も `Dispatchers.IO` 相当の I/O dispatcher 上で実行し、main thread 上で直接 marker/result file を読み書きしない

#### Scenario: DataStore 書き込み完了を待つ
- **WHEN** アプリ起動時に pending restore の DataStore JSON を反映する
- **THEN** システムは DataStore 書き込みが完了するまで `PendingRestoreApplier.runIfNeeded()` から戻らない

#### Scenario: WAL と SHM を cleanup する
- **WHEN** システムが pending restore の DB を live Room DB ファイルとして置換する
- **THEN** システムは live DB path に対応する `-wal` と `-shm` ファイルを best-effort で削除する

#### Scenario: 置換後 DB を検証する
- **WHEN** システムが pending restore の DB を live DB path へ置換する
- **THEN** システムは置換後 DB を読み取り専用で開き、`PRAGMA integrity_check` が `ok` を返すことを確認する

#### Scenario: 置換後検証失敗時に rollback する
- **WHEN** pending restore の DB 置換後検証に失敗する
- **THEN** システムは rollback backup が存在する場合、live DB を置換前の DB へ戻し、pending restore を `failed` として記録する

#### Scenario: DataStore 反映失敗時に DB を rollback する
- **WHEN** pending restore の DB 置換後に DataStore 反映が失敗する
- **THEN** システムは rollback backup が存在する場合、live DB を置換前の DB へ戻し、pending restore を `failed` として記録する

#### Scenario: rollback 時に WAL と SHM を整合させる
- **WHEN** システムが rollback backup から live DB を戻す
- **THEN** システムは置換後に生成された可能性のある live DB の `-wal` / `-shm` を削除し、rollback backup に `-wal` / `-shm` が存在する場合のみそれらを復元する

#### Scenario: rollback copy 失敗時に rollback backup を保持する
- **WHEN** システムが rollback backup から live DB を戻そうとして main DB copy に失敗する
- **THEN** システムは pending restore を失敗として記録し、復旧材料として rollback backup directory を削除しない

#### Scenario: fresh install の置換後検証失敗で壊れた DB を残さない
- **WHEN** live DB main file が存在しない状態で pending DB を live DB path へ copy した後、置換後 DB validation が失敗する
- **THEN** システムは rollback source なしとして failed result を記録し、copy 済み live DB main file と対応する `-wal` / `-shm` を best-effort で削除する

#### Scenario: 成功時に pending と rollback を cleanup する
- **WHEN** pending restore の DB 置換と DataStore 反映が成功する
- **THEN** システムは成功 result file を記録し、pending directory と rollback backup を削除する

#### Scenario: 成功後に再適用しない
- **WHEN** pending restore が成功した次回以降のアプリ起動が行われる
- **THEN** システムは同じ pending restore を再適用しない

#### Scenario: failed pending restore を自動再試行しない
- **WHEN** pending restore marker が `failed` である状態でアプリが起動する
- **THEN** システムは同じ pending restore を自動再試行せず、通常起動を優先する

#### Scenario: stale applying を rollback して失敗扱いにする
- **WHEN** アプリ起動時に pending restore marker が既に `applying` である
- **THEN** システムは同じ pending restore を自動再試行せず、rollback backup があれば live DB を戻し、pending restore を `failed` として記録する

#### Scenario: stale db-swapped を rollback して失敗扱いにする
- **WHEN** アプリ起動時に pending restore marker が既に `db-swapped` である
- **THEN** システムは DataStore 反映を自動継続せず、rollback backup があれば live DB を戻し、pending restore を `failed` として記録する

#### Scenario: DB はマージしない
- **WHEN** システムが Room DB を復元する
- **THEN** システムは既存テーブルへ個別 merge せず、バックアップ DB を復元単位として全上書きする

### Requirement: DataStore の JSON 復元
システムはバックアップ内部の DataStore JSON を DataStore 物理ファイルコピーではなく、DB 非依存の startup restore writer 経由で反映しなければならない（MUST）。

#### Scenario: 設定 DataStore を復元する
- **WHEN** システムが起動時に pending restore を適用する
- **THEN** システムは `datastore/settings.json` の内容を通常設定 DataStore に反映する

#### Scenario: タブ選択 DataStore を復元する
- **WHEN** システムが起動時に pending restore を適用する
- **THEN** システムは `datastore/tabs.json` の `lastSelectedTabsPage` をタブ選択 DataStore に反映する

#### Scenario: DataStore 物理ファイルをコピーしない
- **WHEN** システムが DataStore を復元する
- **THEN** システムは `.preferences_pb` をコピーせず、バックアップ JSON DTO を DB 非依存の DataStore writer 経由で保存する

#### Scenario: Hilt DataSource を起動時復元で使わない
- **WHEN** システムが起動時に pending restore の DataStore JSON を反映する
- **THEN** システムは Hilt 経由の DataSource、Repository、DAO、または `AppDatabase` に依存しない

#### Scenario: DataStore instance を一元管理する
- **WHEN** 通常実行時 DataSource または startup restore writer が settings、tabs、cookies DataStore を取得する
- **THEN** システムは共通 DataStore provider を経由し、同一 process 内で同じ `.preferences_pb` file 用 DataStore instance を複数生成しない

#### Scenario: DataStore provider の初回取得 race で複数 instance を生成しない
- **WHEN** 複数の呼び出し元が同じ process 内で同じ DataStore を初回取得する
- **THEN** システムは同期された初期化により同じ `.preferences_pb` file 用 DataStore instance を 1 つだけ作成する

#### Scenario: startup restore writer が DataStore を直接生成しない
- **WHEN** startup restore writer が settings、tabs、cookies を保存する
- **THEN** writer は `PreferenceDataStoreFactory.create(...)` を直接呼ばず、通常 DataSource と同じ共通 DataStore provider から DataStore を取得する

#### Scenario: JSON に存在しない既知 gesture direction を未割当にする
- **WHEN** 既知の gesture direction が `datastore/settings.json` の `gestureSettings.actions` に存在しない
- **THEN** システムはその direction を未割当として扱い、既存値を保持しない

#### Scenario: 既知 DataStore key を full overwrite する
- **WHEN** システムが backup format に含まれる既知 settings、tabs、または cookies field を DataStore に反映する
- **THEN** システムは JSON の値で既存値を上書きし、必須 field が欠落している場合は既存値を保持せず無効なバックアップとして扱う

#### Scenario: 未知 gesture direction key を拒否する
- **WHEN** `datastore/settings.json` の `gestureSettings.actions` に未知の gesture direction key が存在する
- **THEN** システムは pending restore を作成せず、無効なバックアップとして通知する

#### Scenario: 未知 gesture action を永続化しない
- **WHEN** `datastore/settings.json` の `gestureSettings.actions` に既存 `GestureAction` と一致しない action 値が存在する
- **THEN** システムは未知 action 文字列を DataStore に保存せず、pending restore 作成前の validation で無効なバックアップとして扱う

### Requirement: 復元準備中の UI 状態
システムは pending restore 作成中、同じ画面上のバックアップ作成と復元の重複実行を防ぎ、処理状態をユーザーに示さなければならない（MUST）。

#### Scenario: preview 読み込み中の操作抑制
- **WHEN** システムが選択されたバックアップの preview を読み込んでいる
- **THEN** システムは重複してファイル選択または preview 読み込みを開始できないようにする

#### Scenario: 復元準備中の操作抑制
- **WHEN** pending restore 作成処理が実行中である
- **THEN** システムはバックアップ作成 action、復元ファイル選択 action、確認ダイアログの復元ボタン、Cookie 復元 checkbox を無効化する

#### Scenario: 復元準備中の進捗ダイアログ表示
- **WHEN** pending restore 作成処理が実行中である
- **THEN** システムは復元準備中であることを示すモーダルの進捗ダイアログを表示する

#### Scenario: 復元準備完了を表示する
- **WHEN** pending restore の作成が完了する
- **THEN** システムは復元準備が完了し、アプリ再起動後に復元が適用されることを Snackbar またはダイアログで表示する

#### Scenario: 復元失敗を表示する
- **WHEN** URI open、ZIP 読み込み、pending restore 作成、または起動時の pending restore 適用に失敗する
- **THEN** システムは復元に失敗したことを Snackbar で表示する

#### Scenario: 起動時復元結果を一度だけ表示する
- **WHEN** 起動時 pending restore 適用の結果が result file に記録されている
- **THEN** システムは成功または失敗をユーザーへ一度だけ表示し、表示後に result file を削除する

#### Scenario: 起動時復元結果はバックアップ画面を開かなくても表示する
- **WHEN** 起動時 pending restore 適用の result file が存在し、アプリが任意の初期画面を表示する
- **THEN** システムは backup screen 専用 ViewModel に依存せず、app-level または root-level の通知 owner から成功または失敗を一度だけ表示する

#### Scenario: 無効なバックアップを表示する
- **WHEN** 選択されたファイルがバックアップ形式の検証に失敗する
- **THEN** システムは無効または未対応のバックアップであることを Snackbar で表示する

#### Scenario: 詳細エラーをログに記録する
- **WHEN** preview、pending restore 作成、または起動時 pending restore 適用が失敗する
- **THEN** システムは詳細エラーをログへ記録し、詳細エラー文言や stack trace を画面に表示しない

### Requirement: Repository 層の直列化
システムはバックアップ作成と復元の処理を repository/data 層で直列化しなければならない（MUST）。

#### Scenario: 復元要求を直列化する
- **WHEN** システムが複数の復元要求を同時に受け取る
- **THEN** システムは復元処理を 1 件ずつ直列に実行する

#### Scenario: バックアップ作成と復元を同時実行しない
- **WHEN** バックアップ作成処理または復元処理のいずれかが実行中である
- **THEN** システムはもう一方のバックアップ操作を同じ repository/data 層の mutex で待機させる

### Requirement: 権限不要のファイル入力
システムはバックアップ復元に Storage Access Framework を使用し、追加の外部ストレージ権限を要求してはならない（MUST）。

#### Scenario: 復元で権限ダイアログを出さない
- **WHEN** ユーザーがバックアップファイルを選択する
- **THEN** システムは Android のファイル選択 UI を使用し、外部ストレージ権限要求を表示しない

#### Scenario: 外部ストレージ権限と FileProvider を追加しない
- **WHEN** システムがバックアップファイルを読み込む
- **THEN** システムは `READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE`、`MANAGE_EXTERNAL_STORAGE`、または `FileProvider` をバックアップ復元のために追加または使用しない
