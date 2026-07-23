# pending-restore-result-notification Specification

## Purpose
TBD - created by archiving change notify-pending-restore-result. Update Purpose after archive.
## Requirements
### Requirement: 起動時restore結果の全画面通知
システムは、起動時pending restoreが記録した成功または失敗結果を、現在表示中のrouteに依存しないroot-level Snackbarでユーザーへ通知しなければならない（SHALL）。

#### Scenario: 成功結果を初期画面で通知する
- **WHEN** 起動時pending restoreの成功結果が確定し、アプリの初期画面が表示される
- **THEN** システムはバックアップの復元成功をroot-level Snackbarで通知する

#### Scenario: 失敗結果を初期画面で通知する
- **WHEN** 起動時pending restoreの失敗結果が確定し、アプリの初期画面が表示される
- **THEN** システムはバックアップの復元失敗をroot-level Snackbarで通知する

#### Scenario: 通知中に画面を遷移する
- **WHEN** restore結果Snackbarの表示中にユーザーが別routeへ遷移する
- **THEN** システムはroute固有のSnackbarへ通知を移し替えず、root-level Snackbarで通知を継続する

### Requirement: 確定結果だけを通知する
システムは、pending restore markerとresultの整合を確認し、Room migration検証またはrollback処理によって更新される可能性がある中間結果を成功通知として消費してはならない（MUST NOT）。

#### Scenario: migration完了待ちの中間成功結果が存在する
- **WHEN** markerが`MIGRATION_PENDING`で、resultの`migrationCompleted`がfalseである
- **THEN** システムは成功Snackbarを表示せず、markerが通知可能な状態へ遷移した後にresultを再評価する

#### Scenario: completion checkerが最終成功結果を書き込む
- **WHEN** Room migration後の検証が完了し、markerとresultが最終成功状態になった
- **THEN** システムは最終成功結果を1件の通知候補として公開する

#### Scenario: foreground中にmigration完了を待つ
- **WHEN** Activityが`STARTED`で、即時読取の結果が`MIGRATION_PENDING`の中間結果である
- **THEN** システムは200msから開始して2秒を上限とする指数backoffでresultを継続的に再評価し、`Ready`、`Absent`、`Unreadable`のいずれかで観察を停止して、`Pending`の場合だけ再読を継続する

#### Scenario: migration完了待ちでActivityがSTOPする
- **WHEN** resultが中間状態のままActivityが`STARTED`未満へ遷移する
- **THEN** システムは進行中の観察をcancelしてSTOP中に再読せず、result fileを保持する

#### Scenario: Activityが再びSTARTする
- **WHEN** STOPで観察をcancelした後にActivityが再び`STARTED`へ遷移する
- **THEN** システムは以前のbackoff待機を引き継がずresultを即時に読み、現行として所有してstateへ作用できる観察generationを1つ以下に保つ

#### Scenario: 停止済み観察の読取が遅れて完了する
- **WHEN** STOPまたは再STARTで無効化された観察のreadが、新しい観察開始後に完了する
- **THEN** システムは旧readの完了自体を許容するが、無効な観察からUiStateまたは現行job ownershipを更新せず、次の再読をscheduleしない

#### Scenario: rollback再試行が必要な失敗結果が存在する
- **WHEN** markerが`ROLLBACK_REQUIRED`で、ユーザーへデータ確認を促す失敗resultが記録されている
- **THEN** システムはその失敗結果を成功として扱わず、失敗通知として公開する

### Requirement: 未通知結果のdurable one-shot lifecycle
システムは、未通知resultをapp-level `UiState`として保持し、Snackbarへの表示が完了したresultだけをacknowledgeして削除しなければならない（SHALL）。

#### Scenario: Snackbar表示が完了する
- **WHEN** root-level Snackbarが通知候補の表示を完了する
- **THEN** システムは対応するresultをacknowledgeし、result fileを削除して同一結果を通常の再構成または画面遷移で再通知しない

#### Scenario: 表示完了前にprocessが終了する
- **WHEN** 未通知resultを読み取った後、Snackbar表示の完了とacknowledgeより前にprocessが終了する
- **THEN** システムはresult fileを保持し、次回起動時に通知を再試行する

#### Scenario: 構成変更が発生する
- **WHEN** Snackbar通知中にActivityの構成変更が発生する
- **THEN** app-level state ownerは同じ未通知resultを保持し、同一process内で重複した通知候補を生成しない

#### Scenario: ViewModelがclearされる
- **WHEN** pending resultの観察中にapp-level ViewModelがclearされる
- **THEN** システムは観察jobをcancelし、開始済みの非協調的readが完了しても結果を破棄して、その後の再読、UiState更新、job ownership更新を行わない

#### Scenario: acknowledge対象より新しいresultが存在する
- **WHEN** Snackbar表示中にresult fileが別のrestore結果へ更新される
- **THEN** システムは新しいresultを削除せず、次の通知候補として保持する

### Requirement: 起動とrestore state machineの分離
結果通知の読取・表示・acknowledge失敗は、pending restore適用、Room database open、通常の画面表示を失敗させてはならない（MUST NOT）。

#### Scenario: result fileが存在しない
- **WHEN** アプリ起動時にresult fileが存在しない
- **THEN** システムはSnackbarを表示せず通常起動を継続する

#### Scenario: result JSONが不正である
- **WHEN** result fileをJSONとして解析できない
- **THEN** システムは内部診断へ記録し、成功または失敗を推測して表示せず、同じ不正resultを起動ごとに再処理しない

#### Scenario: result fileの削除に失敗する
- **WHEN** Snackbar表示後のacknowledgeでresult fileを削除できない
- **THEN** システムはアプリを終了させず失敗を内部診断へ記録し、未確認resultとして次回処理可能な状態を維持する

### Requirement: ユーザー向けメッセージと診断情報の分離
Snackbarはlocalizableな固定メッセージでrestoreの成功または失敗を通知し、result fileに含まれる内部例外、filesystem path、rollback診断をそのまま表示してはならない（MUST NOT）。

#### Scenario: 詳細な失敗reasonが記録されている
- **WHEN** result fileに内部診断用の失敗messageが含まれている
- **THEN** Snackbarは一般化した復元失敗メッセージを表示し、詳細messageをUIへ露出しない

