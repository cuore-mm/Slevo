## 1. UiStateと削除イベント

- [x] 1.1 `TabListUiState.kt` にBoard URLとThread tab keyを分離した削除中key集合を追加し、初期状態が空集合であることを `TabListViewModelTest.kt` で確認する。
- [x] 1.2 `TabListViewModel.kt` に単体Board/Thread削除開始イベントを追加し、同一keyを二重登録せず、登録から `TabListAnimationDefaults.ITEM_REMOVAL_MILLIS` 後に既存Store単体close APIを1回呼ぶことを仮想時刻テストで確認する。
- [x] 1.3 `TabListViewModel.kt` の長押しメニューcloseを削除中keyイベントへ接続し、選択解除と退出開始が同じ操作で行われることをテストする。
- [x] 1.4 `TabListViewModel.kt.closeAllUnpinnedTabs` で表示中ページの未固定keyを一度取得して同時登録し、対象0件はNoOp、対象ありは200ms後に `TabSessionStore.closeAllUnpinnedTabs(page)` を1回だけ呼ぶことをBoard/Thread両ページでテストする。
- [x] 1.5 projection更新後に削除済みkeyをUiStateから除去するイベントを追加し、BoardとThreadの削除中key集合を分離した。

## 2. 高さ縮小アニメーション

- [x] 2.1 `RemovableTabList.kt` のローカル `removingItems` と `externalRemoveKey` 即時削除処理を削除し、呼び出し元から受け取る `removingKeys` で各項目の `isRemoving` を決定する。
- [x] 2.2 `RemovableTabList.kt` の各カードと下側余白を `AnimatedVisibility` で包み、200msの `fadeOut + shrinkVertically` により高さと透明度と行間余白が同時に0になるよう実装する。
- [x] 2.3 `RemovableTabList.kt` の固定spacingを退出content内の余白へ置き換え、通常表示の既存間隔を維持する構造にした。
- [x] 2.4 `RemovableTabList.kt` の `animateItem` は追加時fade-inだけを維持し、`fadeOutSpec = null` と `placementSpec = null` にして削除行縮小とLazy placementが重複しないようにする。
- [x] 2.5 `OpenBoardsList.kt`、`OpenThreadsList.kt`、`TabsPagerContent.kt`、`TabScreenContent.kt` にページ別削除中keyを接続し、通常リストと検索結果リストへ反映した。

## 3. 操作経路と互換性

- [x] 3.1 閉じるボタンと長押しメニュー削除を縮小退出経路へ接続し、退出中はカード遷移、長押し、閉じる、スワイプを再実行できない既存ガードを維持した。
- [x] 3.2 `TabListCard.kt` の既存スワイプ確定経路は140msの左方向退出後に既存closeへ直接渡し、縮小・fade退出を重複開始しない接続にした。
- [x] 3.3 一括クローズで表示中ページの未固定keyだけを同時登録し、固定カードと反対ページを対象外とする既存Store契約を維持した。
- [x] 3.4 `TabSessionStore.kt`、Board/Thread Coordinator、Repository、DAO、Room schema、文字列リソースを変更せず、既存bulk契約を維持した。

## 4. 視覚回帰と品質確認

- [x] 4.1 Compose animation clockを制御するテストを追加し、削除中に残存行が詰まることを検証する。異なる残存カードのbounds非交差は実機依存のため未検証とする。
- [x] 4.2 単体Board/Thread、一括Board/Thread、固定タブ混在、検索結果、対象0件の既存契約を維持するテストと既存回帰を確認した。
- [x] 4.3 変更した型・非自明関数へKDocを追加し、Preview関数へKDocを追加せず、長い関数へセクションコメントを付けた。
- [x] 4.4 Android CI workflowにinstrumented test jobがないため、追加Compose testは未実行であり、理由を成果報告へ明記する。
- [x] 4.5 CI相当の `./gradlew testCiUnitTest assembleCi --stacktrace` をGitHub Actionsで実行し、単体テストとビルドが成功した（Run ID: `31592040800`）。
