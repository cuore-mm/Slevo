## Why

スレッド画面のレス本文から別スレッドへのリンクを開くと、スレッドタブ追加と Thread destination 追加が重なり、`HorizontalPager` が更新前のタブ一覧を範囲外参照してクラッシュすることがある。スレッドをタブとして扱う既存設計に合わせ、同一 destination 内の安全なタブ切り替えとして動作を統一する必要がある。

## What Changes

- Thread destination 上でスレッドリンクを開く場合は、対象タブを登録・選択しても Navigation back stack を変更しない。
- 対象スレッドのタブが既に存在する場合は重複作成せず、そのタブを選択する。
- 対象タブを新規作成する場合は、現在選択中のスレッドタブの直後へ永続的に挿入して選択する。
- 選択前後のタブが隣接する場合だけ Pager をアニメーション移動し、離れている場合は即時移動する。
- Pager のページ数、安定キー、ページ内容が同一のタブスナップショットに基づくようにし、タブ追加中の一時的不整合でも範囲外参照しない。
- Thread から Board へ遷移する場合は、操作が pop でも replace でも Thread が右へ退出し、Board が左から復帰する slide-only アニメーションを適用する。
- Board、履歴、ブックマーク、Deep Link など Thread destination 外からスレッドを開く既存の Navigation と Back 動作は維持する。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `handle-thread-link`: スレッド画面内のスレッドリンクを、Navigationを追加しないタブ登録・選択として扱う。
- `separated-board-thread-tab-navigation`: Thread→Threadリンクを同種タブ切り替えとして扱い、Thread routeを履歴へ積む既存要件を変更する。
- `navigation-route-normalization`: 同一Thread destination内では、既存選択だけでなく新規タブ追加時もback stackを追加しない。
- `tab-controller-state-machine`: リンクから開く新規スレッドタブを、現在選択中タブの直後へ整合的に挿入する。
- `tab-reordering`: リンク由来の新規タブに対する挿入位置と永続順序を定める。
- `tab-selection-source-of-truth`: タブ選択時のPager移動方式と、タブ更新中のページ範囲安全性を定める。

## Impact

- 対象: `ThreadScreen.kt`、`ThreadScaffold.kt`、`NavigationExtensions.kt`、`BbsRouteScaffold.kt`、`AppNavGraph.kt`、`TransitionSpecs.kt`、`TabSessionStore.kt`、`ThreadTabsCoordinator.kt`、`TabsRepository.kt`、Thread tab DAO。
- タブ登録APIは、挿入基準となる現在タブkeyとPager移動方式を扱えるよう変更する可能性がある。
- Room上のスレッドタブ順序更新をtransaction内で行う必要がある。
- Unit test、Navigation test、Compose instrumented testを追加する。外部依存関係の追加は予定しない。
