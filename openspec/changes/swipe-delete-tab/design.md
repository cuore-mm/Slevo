## Context

タブ一覧画面は `TabScreenContent` が `PagerState` を保持し、`TabsPagerContent` の `HorizontalPager` で板タブ一覧とスレッドタブ一覧を表示している。下部の `TabListBottomControls` には既に板/スレを明示的に選択する switch があり、ページ切替の入口は下部操作群として成立している。

各タブカードは `OpenBoardsList` / `OpenThreadsList` から `RemovableTabList` 経由で `TabListCard` として描画される。削除は閉じるボタンまたは長押しアクションメニューから既存の `closeBoardTab` / `closeThreadTab` へ流れ、`RemovableTabList` が削除中状態と `animateItem` による退場アニメーションを管理している。固定済みタブは `isPinned` により閉じるボタンが表示されず、固定アイコンのみが表示される。

## Goals / Non-Goals

**Goals:**

- 板/スレ一覧のページ切替を下部 switch のタップ操作へ集約し、横スワイプではページを切り替えない。
- 板タブ・スレッドタブの未固定カードを左右どちらの横スワイプでも削除できる。
- 固定済みタブ、長押し選択中、削除アニメーション中はスワイプ削除を実行しない。
- スワイプ削除でも既存の削除経路と退出アニメーションを使い、閉じるボタンや長押しメニューの挙動を維持する。

**Non-Goals:**

- タブ固定・固定解除の仕様変更。
- タブ削除の永続化方式や repository API の変更。
- タブ一覧以外の板画面・スレッド画面にあるタブスワイプ挙動の変更。
- スワイプ削除後の Undo 機能追加。

## Decisions

### Decision 1: `HorizontalPager` は残し、ユーザースクロールだけを無効化する

`TabsPagerContent` の `HorizontalPager` に `userScrollEnabled = false` を指定し、下部 switch からの `animateScrollToPage` によるページ切替は維持する。`HorizontalPager` を `when` 分岐へ置き換える案もあるが、既存の `PagerState`、下部 switch の選択状態、ページ変更通知を最小変更で維持できるため、ユーザースクロール無効化を採用する。

### Decision 2: スワイプ削除の入口は `TabListCard` に集約する

横スワイプ gesture と視覚的なオフセット表示はカード単位の責務として `TabListCard` に追加する。`OpenBoardsList` / `OpenThreadsList` は、カードがスワイプ削除を確定したときに既存の `requestRemove()` を呼び出すだけにする。

この構成により、板タブとスレッドタブで同じスワイプ挙動を共有でき、削除確定後の状態更新・退場アニメーションは既存の `RemovableTabList` と ViewModel 経路に統一できる。

### Decision 3: スワイプ可否は UI 状態から明示的に渡す

`TabListCard` には `isSwipeDeleteEnabled` のような真偽値を渡し、カード内部では `isSwipeDeleteEnabled && !isPinned` の場合のみ横スワイプを有効にする。呼び出し側では `TabsUiState.isInLongPressSelectionMode` と `isRemoving` を使って通常時以外のスワイプを抑止する。

ViewModel に「スワイプ削除専用」の新状態を増やす案もあるが、今回の可否判定は既存 UI 状態とカード状態から決定できるため、状態追加は避ける。

### Decision 4: 削除確定後は既存の削除フローへ合流する

スワイプがしきい値を超えた場合、カードは `onSwipeDelete` を通知し、一覧側は `requestRemove()` を実行する。これにより閉じるボタンと同じ二重操作防止、削除アニメーション、ViewModel の `closeBoardTab` / `closeThreadTab` を利用する。

## Risks / Trade-offs

- 横スワイプ gesture とカードの `combinedClickable` / 長押しが競合する可能性がある → 横方向のドラッグしきい値を設け、削除確定前はクリック・長押しの既存操作を損なわないようにする。
- `LazyColumn` の縦スクロールとカード横スワイプが競合する可能性がある → 横方向の移動量が優勢な場合のみカードスワイプを処理し、縦スクロールの操作感を検証する。
- 固定タブで反応がないことが分かりにくい可能性がある → 固定済みタブは既存の固定アイコン表示を維持し、スワイプ削除の対象外であることを視覚的に示す。
- `HorizontalPager` を残すことでページ構造は維持される → 今回は挙動変更を最小化するため許容し、将来の責務整理で `when` 表示へ置き換える余地を残す。
