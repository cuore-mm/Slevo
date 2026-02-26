## Context

`ThreadScreen` は主投稿一覧に対する `PostMenuSheet` / `PostItemDialogs` を内部で管理している。
一方で `ThreadScaffold` は `ReplyPopup` 投稿向けの `popupMenuTarget` / `popupDialogTarget` を管理しており、
同じ UI オーバーレイの状態管理が二重化されている。
この二重化はメニュー/ダイアログの挙動差分やバグ修正漏れを誘発しやすい。

## Goals / Non-Goals

**Goals:**
- 主投稿とポップアップ投稿のメニュー/ダイアログ表示を `ThreadScaffold` の共通ホストで一元化する。
- 既存の操作フロー（返信、コピー、NG登録、テキストメニュー）を保持し、挙動を変更しない。
- `ThreadScreen` は投稿イベントの通知のみ担当し、オーバーレイ表示責務を持たない構造にする。

**Non-Goals:**
- `ReplyPopup` の表示ロジックや投稿表示 UI の変更。
- 投稿操作の新規追加や削除。
- `PostMenuSheet` / `PostItemDialogs` の UI デザイン変更。

## Decisions

- **ThreadScaffold にオーバーレイ統合ホストを配置する**
  - 主投稿・ポップアップ投稿を問わず、単一の `PostMenuSheet` / `PostItemDialogs` を管理する。
  - 代替案として `ThreadScreen` へ統合する案は、`ReplyPopup` と表示責務が分離されるため採用しない。

- **ThreadScreen からはイベント通知のみ行う**
  - `PostDialogTarget` を `ThreadScaffold` に引き渡し、実際のメニュー表示とダイアログ遷移はホスト側で行う。
  - 代替案として `ThreadScreen` にホストを置き、`ReplyPopup` から上位へ通知する案は、状態管理が複雑化するため採用しない。

## Risks / Trade-offs

- [Risk] `ThreadScreen` から `ThreadScaffold` へのコールバックが増え、引数配線が複雑になる。
  - Mitigation: `PostActionOverlayHost` などの明示的ホスト関数を導入し、責務を明確化する。

- [Trade-off] `ThreadScaffold` の責務が増えるが、同一 UI の重複管理が解消されるメリットを優先する。

## Migration Plan

1. `ThreadScreen` から `PostMenuSheet` / `PostItemDialogs` の表示責務を除去する。
2. `ThreadScaffold` 側に共通オーバーレイホストを用意し、主投稿とポップアップの双方から通知を受ける。
3. `ReplyPopup` の既存イベント経路を統一ホストへ接続する。
4. 主投稿/ポップアップ双方で同一挙動であることを確認する。

## Open Questions

- なし
