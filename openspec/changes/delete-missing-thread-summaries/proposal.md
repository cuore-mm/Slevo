## Why

`thread_summaries` は板一覧表示のキャッシュであり、履歴・ブックマーク・開いているタブはこのテーブルを直接参照していない。subject.txt から消えたスレッドをアーカイブ行として残し続けると、キャッシュの責務に対して永続データが増え続け、更新処理や将来の保守を複雑にする。

## What Changes

- 板更新時に最新 subject.txt から消えた `thread_summaries` 行は、`isArchived = 1` へ更新せず削除する。
- `thread_summaries` は「現在 subject.txt に存在するスレッド一覧キャッシュ」として扱う。
- 既存 DB に残っている `isArchived = 1` の過去行は、移行または初回クリーンアップで削除する。
- 履歴・ブックマーク・開いているタブ・共通客観状態は、それぞれの独立データとして保持し、summary 削除に巻き込まない。
- `isArchived` カラムの物理削除は別段階で検討し、この変更では削除方式への挙動変更と既存アーカイブ行の整理を優先する。

## Capabilities

### New Capabilities
- `board-thread-summary-retention`: 板一覧キャッシュ `thread_summaries` の保持範囲を、最新 subject.txt に存在する現役スレッドへ限定する振る舞いを定義する。

### Modified Capabilities
- `thread-state-sync`: 板一覧キャッシュから消えたスレッド summary が削除されても、共通客観状態・履歴・ブックマーク・開いているタブの同期と保持が壊れない要件を追加する。

## Impact

- 影響範囲: `BoardRepository.refreshThreadList`、`ThreadSummaryDao`、`ThreadSummaryEntity`、DB migration / cleanup、関連テスト。
- `thread_summaries` の過去アーカイブ行は削除対象になる。
- `isArchived` カラムは当面残す想定だが、新規の板更新処理ではアーカイブ用途に依存しない。
- 履歴、ブックマーク、開いているタブ、`thread_states` のデータ保持仕様は変更しない。
