## Why

全画面TabsとBoard / Thread画面は同じタブを表しているが、現在は独立した横slideで切り替わり、選択カードと表示ページの連続性がない。ブラウザのタブ一覧と同様に、選択した1枚のカードを対応する現在表示ページ全体へ拡大し、Back時は元カードへ縮小するcontainer transformを提供する。

## What Changes

- 全画面Tabsの各板・スレッドカードを、対応するBoard / Threadの現在表示ページ全体とページ専用Shared Boundsで接続する。
- Board / Thread側のidentityはnavigation routeではなく、Pagerのsettle済み現在タブから導出する。
- Tabs側は各`TabListCard`自身の正規化済みboard URLまたは`ThreadId.value`からidentityを導出し、遷移先と同じkeyだけを自動照合する。
- 拡縮対象は現在表示中のページviewport、本文、ページ背景、タイトルを含む下部ツールバー、およびステータスバー保護とする。非表示Pagerページ、Dialog、BottomSheet、Popup、Drawer、Snackbar、アプリ共通chromeは対象外とする。
- タイトルカード、画面種別ボタン、下段アクション行の既存Board↔Thread Shared Boundsはページ遷移と別keyで維持する。
- Tabs↔Board / Thread間の既存横slideを削除し、ページShared Boundsが成立しない場合は短いfadeへフォールバックする。
- Shared Transition専用の選択keyやフレーム待機状態は追加せず、Board / Threadのタブ登録・選択確認完了後にNavigationする。同種別のcontextual Tabs選択では、同一identityを含めて選択先の新destinationを生成し、遷移開始時からpage identityを固定する。
- 既存の最終back stack、contextual Tabs除去、Tabs entry ID guard、`sourceRoute`によるNavigation文脈と初期ページ決定を維持する。選択カードと最終destinationを1回のNavigation transitionで接続するため、同じ最終stackを作る連続pop・push・replaceは単一のpopまたは`popUpTo`付きnavigateへ統合する。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `tablist-ui`: 全画面Tabsカードが、対応するBoard / Threadの現在表示ページ全体へ拡大・縮小する要件を追加する。
- `separated-board-thread-tab-navigation`: Board / Thread / Tabs間の既存back stack規則を維持しながら、Tabs↔BBS間では横slideをページ全体のShared Boundsへ置き換える要件を追加する。

## Impact

- Shared Transition keyとmodifier: `ui/common/transition`配下へページ専用keyとhelperを追加する。
- Navigation: `AppScaffold.kt`の既存`SharedTransitionLayout`を再利用し、`AppNavGraph.kt`からTabsとBoard / Threadへscopeを伝播する。Tabs↔BBSのtransition specだけを変更する。
- Board / Thread共通画面: `BbsRouteScaffold.kt`でsettle済み現在タブをページidentityとして、表示ページviewportへmodifierを適用する。
- Tabs UI: `TabsScaffold.kt`、`TabScreenContent.kt`、`TabsPagerContent.kt`、`OpenBoardsList.kt`、`OpenThreadsList.kt`へscopeと候補状態を伝播する。
- テスト: page key、settled tab、検索crossfade、Pager候補、全画面bounds、Back逆遷移、Navigation stack、既存Board↔Thread / ImageViewer回帰を検証する。
- 新規依存、route型、Room schema、DataStore形式、ユーザー向け文言、アクセシビリティ文言の変更はない。
