## Context
「外部ブラウザで開く」操作は現在 `Intent.ACTION_VIEW` をそのまま起動しており、Slevo が候補に含まれるため再度アプリ内で開いてしまう。

## Goals / Non-Goals
- Goals: Slevo を除外したブラウザのみの選択ダイアログを表示し、ユーザーが外部ブラウザで開けるようにする。
- Non-Goals: 端末全体の既定ブラウザ設定変更、Deep Link のフィルタ挙動変更、Custom Tabs の導入。

## Decisions
- Decision: `ACTION_VIEW + CATEGORY_BROWSABLE` の候補一覧を取得し、Slevo を除外した明示的 Intent で chooser を構築する。
- Alternatives considered:
  - `Intent.createChooser` をそのまま使う: Slevo が候補に残るため不適。
  - 既定ブラウザへ直接遷移: ユーザーの選択機会が無くなるため不採用。

## Implementation Plan
- 追加するユーティリティ
  - 例: `ExternalBrowserUtil.openBrowserChooser(context, url)`
  - 処理手順:
    1. `Intent(Intent.ACTION_VIEW, Uri.parse(url))` に `CATEGORY_BROWSABLE` を付与。
    2. `PackageManager.queryIntentActivities(..., MATCH_DEFAULT_ONLY)` で候補取得。
    3. `context.packageName` を除外。
    4. 残った候補を `Intent` に `setPackage` / `setComponent` して明示的 Intent を構築。
    5. 候補が 0 件ならトースト通知して終了。
    6. 1 件以上なら `Intent.createChooser` で選択ダイアログを表示。
- 呼び出し箇所
  - `ThreadInfoBottomSheet` の「外部ブラウザで開く」処理をユーティリティ経由に変更。

## Risks / Trade-offs
- ブラウザ判定は「BROWSABLE かつ http/https を扱えるアクティビティ」基準のため、まれにブラウザ以外が含まれる可能性がある。

## Validation
- ブラウザ候補が 0 件の場合にトーストが表示されること。
- Slevo が候補に含まれないこと。
- 既存の URL 起動が失敗しないこと。
