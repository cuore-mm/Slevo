## Context

現在の板/スレッド閲覧は、`AppRoute.Board` / `AppRoute.Thread`、`TabSessionStore.boardCurrentPage` / `threadCurrentPage`、`HorizontalPager` の `PagerState.currentPage` がそれぞれ「現在表示中のタブ」を表現している。
その結果、タブ一覧シートから別タブを選択した後の戻る操作、横スワイプ後の再コンポーズ、URL 入力・deep link・登録板一覧からの遷移で、NavController の履歴と実際に表示される Pager ページがずれる可能性がある。

TabsStandalone は削除済みで、板/スレッド画面からのタブ一覧表示は `TabsBottomSheet` に戻っている。このため、独立 route と `popUpTo` の不一致は解消されているが、route / selected page / PagerState の三重管理は残っている。

ユーザー体験としては、板画面からスレッドを開いた場合に戻る操作で板画面へ戻れることを維持したい。そのため、板画面 route とスレッド画面 route は統合せず、画面種別として分離したまま責務を整理する。

## Goals / Non-Goals

**Goals:**

- 板 route とスレッド route を分離したまま、NavController は「画面種別と戻る履歴」を管理する。
- TabSessionStore は「開いているタブ」と「選択中タブ」を stable key で管理する唯一の正本になる。
- PagerState は UI のスクロール状態に限定し、永続的な現在地の正本にしない。
- タブ一覧シート・フルスクリーンタブ一覧・横スワイプによるタブ切り替えでは、不要な NavController back stack を積まない。
- 板からスレッドを開く操作では Thread route を履歴に積み、戻ると直前の Board route に戻る。
- URL 入力、deep link、登録板一覧、ブックマークなどの入口で、タブ登録・選択・画面遷移の責務を明確に分ける。

**Non-Goals:**

- 板画面とスレッド画面を単一 route に統合しない。
- Board / Thread の UI や ViewModel を完全統合しない。
- タブ履歴をシステム戻るボタンに割り当てない。戻るボタンは画面履歴、検索解除、長押し解除、シート閉じを扱う。
- DB スキーマの大規模変更や既存タブ保存形式の全面刷新はこの change の必須範囲にしない。

## Decisions

### 1. Board / Thread route は分離した画面種別として維持する

`AppRoute.Board` と `AppRoute.Thread` は統合せず、NavController の画面種別として維持する。ただし、route 引数は「表示するタブの正本」ではなく、画面復元や入口情報として扱う。

理由:

- 板→スレッド→戻るで板へ戻る体験は NavController の back stack と相性がよい。
- 板とスレッドはツールバー・ボトムバー・ViewModel・初期化処理が異なるため、画面種別として分ける方が責務が明確である。
- route を完全統合すると、戻るで板へ戻るために TabSessionStore 側に selection history を実装する必要があり、Navigation の責務を再実装することになる。

代替案:

- `AppRoute.Tabs` に板/スレッドを完全統合する案。状態管理は単純になるが、板→スレッドの戻る履歴を別途実装する必要があるため採用しない。

### 2. 選択中タブの正本は page index ではなく stable key にする

TabSessionStore に `selectedBoardTabKey` と `selectedThreadTabKey` 相当を導入し、板は正規化済み boardUrl、スレッドは ThreadId を key とする。
Pager の index は、現在の tabs list と selected key から導出する。

理由:

- `currentPage: Int` はタブ追加・削除・並び替え・復元で意味が変わる。
- key はタブの同一性を表すため、Nav route や ViewModel registry の key と整合しやすい。
- 戻る操作で Board route が復元されたときも、route から selected key を補正できる。

代替案:

- `currentPage` を維持して route 変更時だけ補正する案。変更量は小さいが、再コンポーズや削除補正で同じ問題が再発しやすいため根本解決としては不十分。

### 3. ナビゲーション関数を責務別に分割する

現在の `navigateToBoard` / `navigateToThread` は、タブ登録、選択状態更新、NavController.navigate を同時に行う。これを以下の責務に分ける。

- route 正規化: URL や ThreadId をナビゲーション前に正規化する。
- タブ登録: 存在しないタブを追加する。
- タブ選択: selected key を更新する。
- 画面遷移: Board route または Thread route へ navigate / replace / launchSingleTop する。

理由:

- タブ一覧シート選択は「選択変更」であり、通常は back stack を積まない。
- 板からスレッドを開く操作は「画面遷移」であり、back stack を積む。
- 入口ごとに適切な履歴操作を選べるようにする必要がある。

### 4. タブ切り替え操作では back stack を積まない

横スワイプ、タブ一覧シート、フルスクリーンのタブ一覧で既存タブを選択する操作は、TabSessionStore の selected key と active surface を更新するだけにする。
必要に応じて現在の画面種別と選択タブ種別が異なる場合のみ、`launchSingleTop` または現在 surface の置換で Board / Thread 画面へ移動する。

理由:

- タブ切り替えは履歴遷移ではなく、同じ画面種別内の表示状態変更である。
- タブ選択のたびに back stack が増えると、戻る操作がユーザーの期待とずれる。

### 5. `BbsRouteScaffold` は route と currentPage の競合を持たない

`BbsRouteScaffold` は `currentPage` を正本として受け取らず、selected key と tabs list から表示 index を導出する。
ユーザーが Pager をスワイプした場合は、そのページの tab key を TabSessionStore に反映する。
route 変更時は、route に対応する key を selected key に同期してから Pager index を導出する。

理由:

- route と currentPage の優先順位問題をなくす。
- 通常再コンポーズでは PagerState を不必要に route へ戻さない。
- route 変更・戻る復元・タブ削除時だけ selected key を補正する明確なルールにできる。

## Risks / Trade-offs

- [Risk] selected key 導入により既存 coordinator API の呼び出し箇所が広範囲に変わる → Phase を分け、既存 `currentPage` API は一時的に互換ラッパーとして残す。
- [Risk] Board / Thread route 引数と selected key が一時的に不一致になる → route entry 時に key を正規化・選択し、選択に失敗した場合は無効 URL として戻る既存挙動を維持する。
- [Risk] タブ一覧から別種別タブを選んだときの履歴操作が曖昧になる → 既存タブ選択は back stack を積まず、種別が違う場合のみ target surface へ `launchSingleTop` で移動する仕様に固定する。
- [Risk] タブ削除時に selected key が削除済みになる → coordinator が削除後に隣接タブまたは先頭タブの key へ補正し、タブが空なら既存の `onEmptyTabs` 相当の遷移を行う。
- [Risk] deep link / URL / ブックマークなど入口が多く置換漏れしやすい → tasks で呼び出し元を列挙し、各入口ごとに手動検証を必須にする。

## Migration Plan

1. TabSessionStore / coordinator に selected key API を追加し、既存 currentPage API と併存させる。
2. BoardScaffold / ThreadScaffold の route entry 時に、route からタブ登録・selected key 同期を行う。
3. BbsRouteScaffold の initialPage 計算を selected key 導出へ置き換え、Pager スワイプ時は selected key を更新する。
4. タブ一覧シート・フルスクリーンタブ一覧のカード選択を selected key 更新中心に置き換える。
5. `navigateToBoard` / `navigateToThread` の呼び出し元を用途別 API に移行し、必要な箇所だけ NavController の履歴を積む。
6. currentPage API を内部互換レイヤーへ縮小し、不要な route/currentPage 同期処理を削除する。

## Open Questions

- selected board key は正規化済み `boardUrl` のみで十分か、`boardId` がある場合は `boardId` を優先すべきか。
- タブ一覧で別種別タブを選択した場合、現在 surface を置換するか、同種別 surface へ `launchSingleTop` で移動するかの具体的な NavOptions。
- currentPage の永続化が既存 UX として必要か。必要な場合も正本は key とし、index は復元時の fallback として扱う。
