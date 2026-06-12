## Why

板画面・スレッド画面はそれぞれ独立した戻る履歴を持つべきだが、現在は NavController の route、TabSessionStore の currentPage、HorizontalPager の currentPage が同じ「現在表示中のタブ」を別々に管理しており、戻る操作やタブ選択で表示状態がずれる。
板とスレッドの route は分離したまま、route は画面種別と履歴、TabSessionStore は選択中タブ、PagerState は表示スクロールだけを扱うよう責務を整理する。

## What Changes

- 板画面 route とスレッド画面 route は分離したまま維持し、`BoardSurface` / `ThreadSurface` 相当の画面種別として扱う。
- 個別タブの正本は route 引数や page index ではなく、TabSessionStore の selected tab key（板 URL / thread id）に寄せる。
- `navigateToBoard` / `navigateToThread` の責務を、タブを開く処理・タブを選択する処理・NavController の画面遷移に分離する。
- タブ一覧シートや横スワイプによるタブ切り替えでは、NavController の back stack を積まず TabSessionStore の選択状態だけを更新する。
- 板からスレッドを開く操作は NavController の `Thread` 系 route を積み、戻る操作で元の板画面へ戻れるようにする。
- `BbsRouteScaffold` は route と currentPage の競合を避け、selected tab key から Pager の表示 index を導出する。
- TabsStandalone 削除後の BottomSheet ベースのタブ一覧表示を前提に、戻る操作・シート表示・検索解除・長押し解除の優先順位を明確化する。

## Capabilities

### New Capabilities
- `separated-board-thread-tab-navigation`: 板 route とスレッド route を分離したまま、タブ選択状態を TabSessionStore に集約するナビゲーション仕様。
- `tab-selection-source-of-truth`: タブ選択の正本を page index ではなく stable key として管理し、Pager 表示を導出する仕様。

### Modified Capabilities
- `tablist-ui`: タブ一覧カード選択時に NavController の履歴を積まず、対象タブを選択して必要な画面種別へ切り替える要件へ更新する。
- `handle-url-input`: URL 入力から板/スレッドを開く際、タブ登録・選択と画面遷移の責務分離を反映する。
- `handle-thread-link`: 板画面からスレッドを開く際、スレッド route を履歴に積み、戻ると板画面へ戻る要件を明確化する。
- `navigation-route-normalization`: route 正規化とタブ選択更新が混在しないよう、正規化後のタブ登録・選択・画面遷移の責務を分ける。

## Impact

- 影響範囲: `AppRoute`, `AppNavGraph`, `NavigationExtensions`, `BbsRouteScaffold`, `BoardScaffold`, `ThreadScaffold`, `TabsBottomSheet`, `TabScreenContent`, `OpenBoardsList`, `OpenThreadsList`, `TabSessionStore`, `BoardTabsCoordinator`, `ThreadTabsCoordinator`, deep link / URL / thread link 関連処理。
- route の板/スレッド分離は維持するため、板→スレッド→戻るで板へ戻る履歴体験は保持する。
- タブ切り替え操作では back stack を積まないため、既存の戻る履歴とタブ選択履歴を混同しない。
- DB スキーマや外部依存は原則変更しない。必要な状態追加は既存タブ情報・設定の範囲で段階的に行う。
