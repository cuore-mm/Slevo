# Proposal

## Why

返信先プレビューでNG・未取得を除外すると、元の安価行をカードへ置換した際に参照した番号の情報が失われる。また、一レス全体で一件だけの返信先プレビューでは、本文中の複数の会話を元の挿入位置で追えない。

## What Changes

- 安価だけの行ごとに最大一件の返信先を表示する。単一・範囲・カンマ・空白区切りとも元行はカードへ置換する。
- 行ごとに重複を除いた候補を保持し、正常、NG、未取得の順に優先して一件を選ぶ。同種内は安価の出現順とする。
- NG・未取得も「ほかN件」に数え、返信先一覧ポップアップに番号と状態を表示する。従来どおり一覧は選択済み一件も含む全候補を表示する。
- NGは番号・「NGで非表示」・「表示」ボタンを表示する。開示はその場で行い、プレビューなら最大三行のプレビュー、ポップアップなら通常レス形式とする。開示後は「隠す」で戻せる。NG設定は変更しない。
- 安価行ごとに候補を最大1,000件まで保持する。超過時は「ほかN件」の直下と一覧末尾へ「参照先が上限を超えたため、一部を省略しています」と表示する。
- 返信元の件数・一覧・プレビュー、通知は引き続きNGを除外する。ID・ツリー一覧と通常の安価リンクの対象選別は変えない。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `thread-reply-preview`: 安価行ごとの候補、元行置換、状態表示、一時開示、候補上限。
- `thread-reply-relations`: 返信先表示に限り、未取得・NGを番号付き候補として保持する例外を追加する。
- `thread-tree-popup`: 返信先候補一覧では、解決不能な番号を除去せず状態行として残す。

`thread-reply-preview`と`thread-reply-relations`の基点は、未アーカイブだが実装済みの`add-thread-reply-previews`内deltaである。本changeはその実装に依存する後続変更であり、仕様統合は同changeの後に行う。旧artifactsは編集しない。

## Impact

- `app`の`ReplyAnchorParser`、`ThreadReplyPreviewTransformer`、`ReplyPreviewUiState`、`ThreadUiState`、`PopupInfo`、`ThreadRouteViewModel`。
- `PostItemBody`、`ReplyPreviewCard`、`ReplyPopup`、`ThreadScaffold`のイベント接続、状態行用のCompose部品・日本語文字列・Preview。
- parser/transformer/ViewModelのunit test、本文挿入と一覧・開示操作のCompose test。
- DB・永続設定・通信API・通知判定の変更、新規ライブラリは不要。UIの表示契約変更であり、既存の「複数安価行を残す」「レス全体で一件」「全件参照不可なら枠なし」の期待値を置換する。
- 本文とカード間の余白量変更は未確定のため対象外。
