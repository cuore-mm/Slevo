## ADDED Requirements

### Requirement: 投稿成功情報の永続化
システムは既存スレッドへのレス投稿が成功した場合、投稿先固有のレス番号応答に依存せず、対象provider、板、スレッド、投稿内容、照合範囲、投稿時刻、期限を持つ未確定投稿を永続化しなければならない（MUST）。

#### Scenario: 投稿成功時に未確定投稿を保存する
- **WHEN** 既存スレッドへのレス投稿が成功する
- **THEN** システムは `providerId + boardKey + threadKey`、本文、名前、メール、`baseResCount`、`lastCheckedResNum`、`submittedAt`、`expiresAt` を持つ `PENDING` レコードを保存する
- **AND** `lastCheckedResNum` は投稿成功時の `baseResCount` で初期化する

#### Scenario: provider固有レス番号を必要としない
- **WHEN** 投稿成功応答にレス番号が含まれない
- **THEN** システムはレス番号の欠落だけを理由に未確定投稿の保存または後続照合を省略しない

#### Scenario: プロセス再生成後も未確定投稿を維持する
- **WHEN** `PENDING` レコードの照合前にアプリprocessが終了し、その後同じスレッドが再表示される
- **THEN** システムはRoomに保存した未確定投稿を読み込み、照合を再開する

### Requirement: 対象スレッド限定の照合
システムはスレッド取得成功時、取得したスレッドと `providerId + boardKey + threadKey` が完全一致する `PENDING` レコードだけを照合しなければならず（MUST）、別スレッドの未確定投稿を検証してはならない（MUST NOT）。

#### Scenario: 同じスレッドの未確定投稿を照合する
- **WHEN** スレッド取得に成功し、同じ `providerId + boardKey + threadKey` の `PENDING` レコードが存在する
- **THEN** システムはそのレコードを取得レスと照合する

#### Scenario: 別スレッドの未確定投稿を照合しない
- **WHEN** スレッド取得に成功し、異なるprovider、板、またはthread keyの `PENDING` レコードだけが存在する
- **THEN** システムはそれらのレコードを読み込みまたは照合しない

#### Scenario: 新しいレスがない場合は照合を省略する
- **WHEN** 取得レス数が `lastCheckedResNum` 以下である
- **THEN** システムは投稿内容の比較と状態更新を行わず、`PENDING` を維持する

### Requirement: 未確認レス範囲の照合
システムは各未確定投稿について、`max(baseResCount + 1, lastCheckedResNum + 1)` から現在の取得レス数までを照合候補範囲としなければならない（MUST）。

#### Scenario: 候補がない場合に確認位置を進める
- **WHEN** 未確認レス範囲に一致候補が存在しない
- **THEN** システムは状態を `PENDING` のまま維持し、`lastCheckedResNum` を現在の取得レス数へ更新する

#### Scenario: 一意な候補を確定する
- **WHEN** 未確認レス範囲に一致候補が1件だけ存在する
- **THEN** システムは候補のレス番号を `matchedResNum` として状態を `MATCHED` へ遷移させる
- **AND** 候補レスの日時と投稿IDを使って既存の投稿履歴を保存する

#### Scenario: 複数候補を推測で確定しない
- **WHEN** 未確認レス範囲に一致候補が2件以上存在する
- **THEN** システムは状態を `PENDING` のまま維持し、`lastCheckedResNum` を進めず、いずれの候補も自分の投稿として確定しない

### Requirement: provider非依存の一致判定
システムは正規化後の本文完全一致を必須条件とし、投稿時に入力された非空の名前とメールを追加の完全一致条件として使用しなければならない（MUST）。投稿時に空だった名前またはメールは一致条件にしてはならない（MUST NOT）。

#### Scenario: 本文と入力済みidentityが一致する
- **WHEN** 改行と行末空白を正規化した本文が完全一致し、投稿時に入力された非空の名前とメールもtrim後に完全一致する
- **THEN** システムはそのレスを一致候補として扱う

#### Scenario: 空の名前とメールをwildcardとして扱う
- **WHEN** 正規化後の本文が完全一致し、投稿時の名前またはメールが空である
- **THEN** システムは空だった項目について取得レス側の既定値との差を不一致理由にしない

#### Scenario: 本文の意味を変える正規化を行わない
- **WHEN** 本文が大小文字、行内空白、または行構造だけ異なる
- **THEN** システムは空白畳み込み、大小文字変換、行結合で本文を同一視しない

### Requirement: 未確定投稿の期限とterminal cleanup
システムは未確定投稿を投稿から24時間後に `EXPIRED` とし、`MATCHED` または `EXPIRED` になってから長期間経過したレコードを通常照合から除外して削除しなければならない（MUST）。

#### Scenario: 期限超過をEXPIREDにする
- **WHEN** `PENDING` レコードの照合時刻が `expiresAt` 以上である
- **THEN** システムはそのレコードを `EXPIRED` へ遷移させ、レス候補を照合しない

#### Scenario: terminal状態を照合しない
- **WHEN** 対象スレッドに `MATCHED` または `EXPIRED` のレコードが存在する
- **THEN** システムはそれらを通常の一致判定対象に含めない

#### Scenario: 古いterminal状態を削除する
- **WHEN** `MATCHED` または `EXPIRED` のレコードの投稿時刻が現在時刻から30日より古い
- **THEN** システムはpending作成または対象スレッド照合時の保守処理でそのレコードを削除する

### Requirement: 自分の投稿確定の原子性と表示反映
システムは未確定投稿の `MATCHED` 遷移と既存投稿履歴への保存を単一transactionで実行し、同じ未確定投稿を複数回投稿履歴へ保存してはならない（MUST NOT）。確定後は既存の自分の投稿番号監視によって現行マークを表示しなければならない（MUST）。

#### Scenario: 確定と履歴保存が成功する
- **WHEN** 一意な一致候補について状態遷移と投稿履歴保存がともに成功する
- **THEN** transactionをcommitし、既存の `myPostNumbers` 監視へ候補レス番号を反映する
- **AND** 投稿行、返信ポップアップ、ミニマップは既存の自分の投稿マークを表示する

#### Scenario: 履歴保存失敗時にPENDINGを維持する
- **WHEN** `MATCHED` 遷移と同じtransaction内の投稿履歴またはidentity履歴保存が失敗する
- **THEN** システムはtransaction全体をrollbackし、未確定投稿を `PENDING` のまま維持する

#### Scenario: 解決済みレコードを二重確定しない
- **WHEN** 同じ未確定投稿に対する確定処理が再度実行される
- **THEN** 条件付き状態更新は失敗し、追加の投稿履歴を保存しない
