## Context

現在のスレッド状態は、板一覧キャッシュの `thread_summaries`、閲覧履歴の `thread_histories`、開いているスレッドタブの `open_thread_tabs` に分散している。板画面は `thread_summaries.resCount` と履歴の `resCount` から新着レス数を計算し、タブ一覧は `open_thread_tabs.resCount` と一時的な `_newResCounts` から新着レス数を表示している。

この構造では、板更新・タブ一覧更新・スレッド閲覧のどの経路で更新されたかによって参照する値が異なり、同一スレッドでも画面間で新着レス数や既読状態が一致しない。Room DB の既存データを保持したまま、スレッド単位の状態を正規化する必要がある。

## Goals / Non-Goals

**Goals:**
- スレッドごとの最新レス数・最終既読レス番号・最初の新着レス番号を、共通の永続状態として管理する。
- 板画面とタブ一覧の新着レス数を、同じ計算規則と同じ DB 状態から導出する。
- `open_thread_tabs` をタブ固有状態中心のテーブルへ寄せ、レス数・既読状態の正本を共通スレッド状態へ移す。
- 既存 DB から安全に移行し、既存のタブ順・スクロール位置・履歴情報を維持する。

**Non-Goals:**
- スレッド本文・レス本文のキャッシュ方式は変更しない。
- subject.txt や dat の通信方式、差分取得方式は変更しない。
- タブ一覧 UI の見た目そのものは、共通状態を参照するために必要な表示更新以外は変更しない。
- お気に入り、投稿履歴、NG 設定の DB 設計は対象外とする。

## Decisions

### Decision 1: スレッド状態の正本テーブルを導入する

`thread_states` のような共通テーブルを追加し、スレッド ID を主キーとして以下の状態を保持する。

- `threadId`: `ThreadId` 形式の一意キー
- `boardId` / `boardUrl`: 板との関連付け
- `title`: スレッドタイトルの最新表示名
- `latestResCount`: 板更新・タブ更新・スレッド閲覧で確認した最新レス数
- `prevResCount`: 新着範囲を作る直前のレス数
- `lastReadResNo`: ユーザーが最後に既読化したレス番号
- `firstNewResNo`: 新着レス範囲の開始番号。新着がなければ `null`
- `updatedAt`: 最後に状態を更新した時刻

代替案として `thread_histories` を正本にする方法もあるが、履歴は「閲覧したことがあるスレッド」を表す意味を持っており、未閲覧でも板更新やタブ更新で状態を持ちたいケースと責務が混ざる。タブテーブルを正本にする方法もあるが、閉じたタブの状態が失われるため、板画面との同期元として不適切である。

### Decision 2: `open_thread_tabs` はタブ固有状態に限定する

`open_thread_tabs` には `threadId`、`sortOrder`、スクロール位置、必要に応じて表示に必要な補助情報のみを残し、レス数・既読状態は共通スレッド状態から JOIN または Repository の合成で読み出す。

代替案として `open_thread_tabs` にレス数を残して都度同期する方法もあるが、二重管理が継続し、同期漏れが再発する。タブ一覧の表示モデルは `open_thread_tabs` と `thread_states` を合成して作る。

### Decision 3: 新着レス数は共通の計算規則で導出する

板画面・タブ一覧ともに、新着レス数は `latestResCount`、`lastReadResNo`、`firstNewResNo` から導出する。基本規則は以下とする。

- `firstNewResNo` が有効な場合は `latestResCount - firstNewResNo + 1` を 0 以上に丸める。
- `firstNewResNo` が無効な場合は `latestResCount - lastReadResNo` を 0 以上に丸める。
- `latestResCount` が `lastReadResNo` 以下の場合、新着レス数は 0 とする。

代替案として画面ごとに差分計算を残す方法もあるが、同期要件の中心が「同じ値を表示する」ことであるため、計算規則を共有する方が保守しやすい。

### Decision 4: 更新経路は同じ Repository API に集約する

板更新、タブ一覧更新、スレッド閲覧・更新、既読位置更新は、共通スレッド状態を更新する Repository API を経由する。各画面の Coordinator は DB テーブルを直接意識せず、状態更新の意図を Repository に渡す。

代替案として各 DAO を既存 Coordinator から直接更新する方法もあるが、トランザクション境界と新着計算が分散するため、今回の同期要件に合わない。

### Decision 5: 移行時は既存値の最大情報を優先する

マイグレーションでは、既存の `thread_histories`、`open_thread_tabs`、`thread_summaries` から `thread_states` を作成する。レス数は利用可能な値の最大値を採用し、既読状態は既存の `lastReadResNo` と `firstNewResNo` を優先する。タイトル・板情報は、履歴またはタブに存在する詳細情報を優先し、不足する場合は板キャッシュの情報で補完する。

代替案として履歴のみを移行元にする方法もあるが、未履歴の開いているタブや板キャッシュの最新レス数を失う可能性がある。

## Risks / Trade-offs

- [Risk] Room マイグレーションで既存データの統合条件が複雑になる → マイグレーションテストでタブのみ、履歴のみ、板キャッシュのみ、複数に存在するスレッドのケースを検証する。
- [Risk] `ThreadId` 生成に必要な host/board/threadKey が移行元によって不足する → 既存の `ThreadId` カラムを優先し、板キャッシュ由来の行は `boardUrl` から生成できるものだけ移行対象にする。
- [Risk] JOIN や Flow 合成が増えて一覧表示の負荷が増える → `thread_states.threadId`、`boardId`、`boardUrl` に必要な index を付与し、Repository 側では必要な板・開いているタブに絞って監視する。
- [Risk] 既読更新とレス数更新が競合して新着範囲が不整合になる → DB トランザクション内で現在状態を読み取り、最新レス数と既読レス番号を同時に評価して更新する。
- [Risk] `thread_histories.resCount` の意味が変わる場合に既存履歴画面へ影響する → 履歴画面が表示に必要な値を共通スレッド状態から補完するか、履歴側の値を履歴メタ情報として残すかを実装時に明確化する。

## Migration Plan

1. 新しい `thread_states` テーブルを作成し、主キーと必要な index を定義する。
2. 既存 `thread_histories` と `open_thread_tabs` から、`threadId` ごとの状態候補を作成する。
3. `thread_summaries` から板キャッシュ上のレス数・タイトルを補完し、既存候補がある場合は `latestResCount` の最大値を採用する。
4. `open_thread_tabs` はタブ固有状態を保持する新テーブルへ移行し、レス数・既読状態カラムを参照しない構造へ移す。
5. Repository と Coordinator を共通スレッド状態へ接続し、既存 UI モデルには互換的に `resCount`、`firstNewResNo`、`lastReadResNo`、`newResCount` を供給する。
6. マイグレーションテストと状態同期テストを追加し、既存ユーザーの代表的なデータで同期結果を確認する。

Rollback は DB バージョンを進める変更のため、アプリ内で旧スキーマへ自動的に戻すことは想定しない。問題発生時は修正マイグレーションを追加し、共通スレッド状態を再計算できるようにする。

## Open Questions

- `thread_histories.resCount` を削除または非正本化するか、履歴画面用のスナップショットとして残すか。
- 板キャッシュ由来で `ThreadId` を安全に生成できない既存行を移行対象外にするか、補助的な変換ルールを追加するか。
- `firstNewResNo` の初期値を、既読済みスレッドでは `lastReadResNo + 1` にするか、既存値がない場合は `null` にして新着なしとして扱うか。
