## 1. UiStateと削除イベント

- [ ] 1.1 `TabListUiState.kt` にBoard URLとThread tab keyを分離した削除中key集合を追加し、初期状態が空集合であることを `TabListViewModelTest.kt` で確認する。
- [ ] 1.2 `TabListViewModel.kt` に単体Board/Thread削除開始イベントを追加し、同一keyを二重登録せず、登録から `TabListAnimationDefaults.ITEM_REMOVAL_MILLIS` 後に既存Store単体close APIを1回呼ぶことを仮想時刻テストで確認する。
- [ ] 1.3 `TabListViewModel.kt` の長押しメニューcloseを既存 `pendingCloseBoardTab` / `pendingCloseThreadTab` 経路から削除中keyイベントへ接続し、選択解除と退出開始が同じ操作で行われることをテストする。
- [ ] 1.4 `TabListViewModel.kt.closeAllUnpinnedTabs` で表示中ページの未固定keyを一度取得して同時登録し、対象0件はNoOp、対象ありは200ms後に `TabSessionStore.closeAllUnpinnedTabs(page)` を1回だけ呼ぶことをBoard/Thread両ページでテストする。
- [ ] 1.5 projection更新後に削除済みkeyをUiStateから除去するイベントを追加し、BoardとThreadの同一文字列keyが互いの削除中状態へ影響しないことをテストする。

## 2. 高さ縮小アニメーション

- [ ] 2.1 `RemovableTabList.kt` のローカル `removingItems` と `externalRemoveKey` 即時削除処理を削除し、呼び出し元から受け取る `removingKeys` で各項目の `isRemoving` を決定する。
- [ ] 2.2 `RemovableTabList.kt` の各カードと下側余白を `AnimatedVisibility` で包み、200msの `fadeOut + shrinkVertically` により高さと透明度と行間余白が同時に0になるよう実装する。
- [ ] 2.3 `RemovableTabList.kt` の `Arrangement.spacedBy` を退出content内の余白へ置き換え、通常表示の既存間隔を維持しつつ、退出完了時に固定余白分の位置ジャンプが発生しないことをComposeテストで確認する。
- [ ] 2.4 `RemovableTabList.kt` の `animateItem` は追加時fade-inだけを維持し、`fadeOutSpec = null` と `placementSpec = null` にして削除行縮小とLazy placementが重複しないようにする。
- [ ] 2.5 `OpenBoardsList.kt`、`OpenThreadsList.kt`、`TabsPagerContent.kt`、`TabScreenContent.kt` にページ別削除中keyと削除開始callbackを接続し、通常リストと検索結果リストで同じkeyの退出状態が反映されることを確認する。

## 3. 操作経路と互換性

- [ ] 3.1 閉じるボタンと長押しメニュー削除を縮小退出経路へ接続し、退出中はカード遷移、長押し、閉じる、スワイプを再実行できないことをCompose/ViewModelテストで確認する。
- [ ] 3.2 `TabListCard.kt` の既存スワイプ確定経路は140msの左方向退出後に既存closeへ直接渡し、縮小・fade退出を重複開始しないことを回帰テストで確認する。
- [ ] 3.3 一括クローズで表示中ページの未固定カードだけが同時に退出し、固定カードと反対ページが表示・状態とも不変であることをCompose/ViewModelテストで確認する。
- [ ] 3.4 `TabSessionStore.kt`、Board/Thread Coordinator、Repository、DAO、Room schema、文字列リソースに差分がなく、bulkが1 command・1 pending・1 Repository callの既存契約を維持することをdiffと既存テストで確認する。

## 4. 視覚回帰と品質確認

- [ ] 4.1 Compose animation clockを制御するテストを追加し、削除開始時・中間時・完了時に対象カード高が単調に縮小し、削除前から可視だった残存カードと画面上端外から入る残存カードのboundsが交差しないことを検証する。
- [ ] 4.2 単体Board/Thread、一括Board/Thread、固定タブ混在、検索結果、対象0件の各ケースで既存選択・メニューdismiss・NoOp契約を維持する。
- [ ] 4.3 変更した型・非自明関数へアノテーションより上にKDocを追加し、Preview関数はコメントなし、30行超関数はセクションコメント付きであることを確認する。
- [ ] 4.4 Android実行環境で追加Compose testを `connectedDebugAndroidTest` の対象クラス指定で実行する。環境がなければ未実行理由を成果報告へ明記する。
- [ ] 4.5 CI相当の `./gradlew testCiUnitTest assembleCi --stacktrace` を実行し、単体テストとビルドが成功することを確認する。
