## Why

板画面とスレッド画面は下部コントローラーから相互に切り替えられるが、現在はタイトルカードと画面種別ボタンの視覚的な連続性がない。既存のShared Transition基盤を利用し、別destinationのまま現在の板・スレッドを示す要素が自然に位置とサイズを変える遷移を追加する。

## What Changes

- Board表示時のBoardタイトルカードとThread表示時のBoardボタンを、板タブidentity単位の`sharedBounds`で接続する。
- Board表示時のThreadボタンとThread表示時のThreadタイトルカードを、スレッドタブidentity単位の`sharedBounds`で接続する。
- Board/Threadで内容が異なる下段ツール群は、個別アイコンではなく`BottomActionsRow`全体を共通keyの`sharedBounds`で接続し、行内コンテンツをfadeでクロスフェードする。
- Shared Transition対象をsettle済みタイトルカードと有効な画面種別ボタンに限定し、同種タブPagerのドラッグ中は対象にしない。
- Board/Thread用の型付きshared keyと共通Modifierを共通UI層へ追加し、両画面で同じ照合規則と描画設定を利用する。
- `AppRoute.Board` / `AppRoute.Thread`、既存のpush・pop・replace、TabSessionStore、Pager、検索・縮退状態を維持する。
- Board↔Thread間のNavigationは既存方向と時間を保ったslide-onlyとし、その他のdestination間Navigationは従来のtransitionを維持する。
- Shared Boundsは標準overlay描画から開始し、既存ImageViewerのShared Transition設定は変更しない。
- Navigation時の5ch.netから5ch.ioへの正規化によって遷移元と遷移先のTabInfo identityが異なる場合は、誤った要素を接続せず通常の画面遷移へフォールバックする。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `separated-board-thread-tab-navigation`: 下部コントローラーからBoard/Threadを切り替える際のタイトルカード、画面種別ボタン、下段アクション行のShared Bounds、および不一致時のフォールバック要件を追加する。

## Impact

- `app/src/main/java/com/websarva/wings/android/slevo/ui/common/transition/`: Board/Threadコントローラー用shared keyと共通Modifierを追加する。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/common/TabToolBar.kt`: 画面種別ボタンのroot CardへModifierを渡せるようにする。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/common/TabToolBar.kt`: 下段`BottomActionsRow`のroot RowへModifierを渡せるようにする。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffold.kt`: settle済みタイトルカードだけをShared Transition候補として識別できるようにする。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/navigation/AppNavGraph.kt` と `TransitionSpecs.kt`: Board↔Thread間だけNavigationのfadeを外し、slide-onlyへ切り替える。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/board/` と `ui/thread/`: 既存scope、TabInfo identity、共通Modifierをタイトルカードと画面種別ボタンへ接続する。
- `app/src/test/` と `app/src/androidTest/`: key照合、対象限定、Navigation・Pager・ImageViewer回帰を検証する。
- 外部依存、route引数、永続データ形式、公開APIの破壊的変更はない。
