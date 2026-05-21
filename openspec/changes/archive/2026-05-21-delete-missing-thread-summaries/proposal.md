## Why

`thread_summaries` は板一覧表示のキャッシュであり、履歴・ブックマーク・開いているタブはこのテーブルを直接参照していない。subject.txt から消えたスレッドをアーカイブ行として残し続けると、キャッシュの責務に対して永続データが増え続け、更新処理や将来の保守を複雑にする。

## What Changes

- 板更新時に最新 subject.txt から消えた `thread_summaries` 行は、`isArchived = 1` へ更新せず削除する。
- `thread_summaries` は「現在 subject.txt に存在するスレッド一覧キャッシュ」として扱う。
- `isArchived` カラムを `thread_summaries` から削除し、アーカイブ状態を持たないテーブル定義へ移行する。
- 既存 DB の移行時には `isArchived = 0` の現役 summary だけを新テーブルへコピーし、`isArchived = 1` の過去行は破棄する。
- 履歴・ブックマーク・開いているタブ・共通客観状態は、それぞれの独立データとして保持し、summary 削除に巻き込まない。

## Capabilities

### New Capabilities
- `board-thread-summary-retention`: 板一覧キャッシュ `thread_summaries` の保持範囲を、最新 subject.txt に存在する現役スレッドへ限定する振る舞いを定義する。

### Modified Capabilities
- `thread-state-sync`: 板一覧キャッシュから消えたスレッド summary が削除されても、共通客観状態・履歴・ブックマーク・開いているタブの同期と保持が壊れない要件を追加する。

## Impact

- 影響範囲: `BoardRepository.refreshThreadList`、`ThreadSummaryDao`、`ThreadSummaryEntity`、DB migration、関連テスト。
- `thread_summaries` の過去アーカイブ行と `isArchived` カラムは削除対象になる。
- 履歴、ブックマーク、開いているタブ、`thread_states` のデータ保持仕様は変更しない。
