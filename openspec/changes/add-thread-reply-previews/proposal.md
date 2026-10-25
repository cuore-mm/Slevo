# Proposal

## Why

スレッドの返信関係を知るために毎回ポップアップを開く必要があり、番号順で読み進める際に前後の会話を追いにくい。また、本体一覧の返信件数にNGレスが含まれ、NGレスからの返信通知も届いてしまう。

## What Changes

- 番号順のスレッド本体に、安価だけの行から選ぶ返信先プレビューをレス全体で最大1件、当該レスへの返信プレビューを古い番号順で最大3件追加する。ツリー順・ポップアップ内には追加しない。
- 単一安価は番号とプレビューを一体化し、範囲・カンマ・複数安価の行はその下に独立したプレビュー枠を置く。超過分は「ほか○件」から確認できるようにする。
- プレビューはレス番号・独自の名前・IDのヘッダー、その下に左本文と右画像を配置する。本文のみ最大3行、日付とデフォルト名と画像URLは省略し、画像は先頭1枚と追加枚数のバッジを表示する。返信元本文全体で安価tokenが一件だけの場合に限り、それが親レス番号だけの単一安価ならtokenと直後の空白を省く。本文内に複数安価tokenがある場合は、同じ行・別行を問わず一部だけを省略せず、全て保持する。範囲・カンマ安価も保持する。返信元プレビューはグループ見出し・囲み背景を使わず、左の縦線と各項目間の区切り線で表現する。
- `>>n`、`>>a-b`、`>>a,b`を共通の規則で解析し、返信関係・リンク操作・通知の参照先を揃える。通知の範囲非展開仕様を変更する。
- レス番号横の返信件数と返信一覧は、表示可能なNG除外済みレスだけを数える。NG変更時にも直ちに更新する。
- 画面と共通のNG判定でNGレス由来の新規通知を抑止し、配信待ち通知の配信時にも確認する。配信済み通知の削除は行わない。

## Capabilities

### New Capabilities

- `thread-reply-relations`: 安価構文と参照先の共通解釈、NG除外済みの返信元一覧・返信件数の整合。
- `thread-reply-preview`: 番号順本体における返信先・返信元プレビューの選択、レイアウト、省略、操作、アクセシビリティ。

### Modified Capabilities

- `reply-notification`: 範囲・カンマ安価を含む新着返信の判定と、両取得経路でのNGレス由来通知の抑止。

## Impact

- `app`の`data/util/ReplyAnchorParser.kt`、NG共通判定、`data/notification/ReplyNotificationCandidate.kt`、`ui/thread/viewmodel/ThreadRefreshUseCase.kt`と通知関連テスト。
- `ui/thread/viewmodel/ThreadDisplayTransformers.kt`、`ThreadRouteViewModel.kt`、`ui/thread/state/ThreadUiState.kt`、プレビュー専用モデル・派生処理。
- `ui/thread/res/PostItem*.kt`、`ui/thread/screen/components/ThreadPostListContent.kt`、`ui/util/LinkUtils.kt`、新規プレビュー部品、画像文脈キーと既存ビューアへの引き継ぎ。
- プレビュー用日本語リソース、Compose Preview、ユニット・Compose UIテスト。新規ライブラリ、DBスキーマ変更、バックグラウンド取得、永続設定追加は不要。
- 既存のツリー親選択規則、更新グループ、通常レス表示、ポップアップ操作、画像ビューアの同一レス内スワイプ契約は維持する。
