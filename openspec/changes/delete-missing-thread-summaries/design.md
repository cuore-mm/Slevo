## Context

`thread_summaries` は板画面の一覧表示に使うローカルキャッシュで、現在の表示クエリは `isArchived = 0` の行だけを対象にしている。履歴、ブックマーク、開いているスレッドタブ、共通客観状態はそれぞれ `threadId`、`boardUrl`、`threadKey`、スナップショット情報を保持しており、`thread_summaries` の行を外部キーとして直接参照していない。

直近の修正では、subject.txt から消えたスレッドを大量にアーカイブする際の SQL 変数上限問題を回避した。しかし、アーカイブ済み行を保持し続ける設計自体は、キャッシュテーブルに過去データを蓄積し続けるため、DB サイズと更新処理の複雑さを残す。

## Goals / Non-Goals

**Goals:**
- `thread_summaries` を「最新 subject.txt に存在する現役スレッドのキャッシュ」として単純化する。
- subject.txt から消えた summary はアーカイブではなく削除する。
- `isArchived` カラムを削除し、既存 DB に残る `isArchived = 1` の過去 summary を migration で破棄する。
- summary 削除後も履歴、ブックマーク、開いているタブ、共通客観状態を保持する。
- 大量削除でも SQLite の変数上限を超えず、板更新トランザクション内で安全に処理する。

**Non-Goals:**
- 履歴、ブックマーク、タブ、`thread_states` の保存形式は変更しない。
- スレッド本文や dat キャッシュの削除ポリシーは扱わない。

## Decisions

### 1. subject.txt から消えた summary は削除する

板更新時の差分計算で「更新開始時点の summary ID − 最新 subject.txt の ID」を削除対象とする。削除対象は `DELETE FROM thread_summaries WHERE boardId = :boardId AND threadId IN (:threadIds)` のような板内 ID 条件で削除する。

代替案として、アーカイブ方式を維持しつつ後続 GC で削除する方法がある。しかし、アーカイブ済み summary を表示や復元に使っていない現状では、二段階にする価値が低く、更新処理とデータ保持方針を複雑にする。

### 2. 存在判定は現役 summary を基準にする

削除方式では、subject.txt に存在しない summary は保持しない。したがって、次回 subject.txt に再出現したスレッドは新規 summary として挿入される。これにより `firstSeenAt` は再出現時点になり、板一覧上では新しく見えたスレッドとして扱われる。

アーカイブ方式のように `firstSeenAt` を保持して復活扱いにする案もあるが、キャッシュの責務を「現在一覧」に限定するなら、新規挿入の方が一貫している。履歴やブックマークに必要な過去情報は別テーブル側のスナップショットで保持する。

### 3. `isArchived` カラムは migration で削除する

既存 DB には `isArchived = 1` の行が残っている可能性がある。`thread_summaries` の責務を現役一覧キャッシュに限定するため、migration で `isArchived` を持たない新テーブルへ再作成し、`isArchived = 0` の行だけをコピーする。

SQLite の互換性を考慮し、単純な `DROP COLUMN` ではなく「新テーブル作成 → 現役行コピー → 旧テーブル削除 → リネーム → index 再作成」を基本手順とする。これにより、既存の過去アーカイブ行は migration 中に破棄され、以後の通常処理は `isArchived` に依存しない。

### 4. `isArchived` 依存クエリを削除する

`ThreadSummaryDao` の表示クエリ、既存行更新、削除対象取得、`ThreadStateDao` / `DatabaseCallback` の `thread_summaries` JOIN 条件から `isArchived` 条件を削除する。`thread_summaries` に存在する行はすべて現役 summary とみなす。

### 5. 削除処理もチャンク分割する

削除対象 ID が多数になる板でも SQLite の変数上限を超えないよう、削除 DAO 呼び出しはチャンク単位に分割する。分割削除は既存の板更新トランザクション内で行い、途中失敗時に板一覧キャッシュが部分確定しないようにする。

## Risks / Trade-offs

- [Risk] 再出現したスレッドの `firstSeenAt` が更新され、新着スレ扱いになる。 → キャッシュから消えた時点で過去 summary を破棄する仕様として明文化し、テストで期待値を固定する。
- [Risk] `thread_summaries` のテーブル再作成 migration に失敗すると板一覧キャッシュが失われる。 → migration 手順をテストし、履歴・ブックマーク・タブ・共通客観状態が独立して保持されることを確認する。
- [Risk] `thread_states` の GC が現役 summary を参照保持条件としているため、削除タイミングによって孤立状態が増える。 → 現役 summary だけを保持条件とする既存仕様は維持し、共通客観状態の遅延 GC が履歴・ブックマーク・タブ参照を確認することを前提にする。
- [Risk] 将来、アーカイブ済み summary を使う機能を追加しにくくなる。 → 過去情報が必要な機能は履歴・ブックマーク・`thread_states` を正本として使う方針を明確にする。

## Migration Plan

1. `ThreadSummaryDao` に削除用 DAO メソッドを追加する。
2. 板更新時の差分処理を `markArchived` から削除処理へ切り替える。
3. `thread_summaries` を `isArchived` なしのスキーマへ再作成する migration を追加し、既存 `isArchived = 0` 行だけをコピーする。
4. `ThreadSummaryEntity`、DAO、JOIN、index から `isArchived` 依存を削除する。
5. 大量削除、通常更新、新規追加、再出現スレッド、履歴・ブックマーク保持、migration のテストを追加する。

ロールバック時は、削除済み summary を復元できない。ただし履歴・ブックマーク・タブ・共通客観状態は独立して保持されるため、ユーザーの主要データは維持される。

## Open Questions

- 再出現スレッドを新着扱いにすることを UI 仕様として許容するか。
