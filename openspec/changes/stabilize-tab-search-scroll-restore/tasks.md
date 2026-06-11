## 1. 計画変更の整理

- [x] 1.1 既存の snapshot / restore 中心の復元方針を見直し、通常リストと検索結果リストを分離する方針へ変更する
- [x] 1.2 通常リスト用 `LazyListState` と検索結果用 `LazyListState` を分ける設計判断を design.md に反映する
- [x] 1.3 通常リスト復元用 snapshot / pending restore は削除対象として整理する

## 2. 状態モデルの見直し

- [x] 2.1 `TabListUiState` から通常リスト復元待ち状態を削除する
- [x] 2.2 通常リスト復元用の `TabSearchScrollSnapshot` を削除する
- [x] 2.3 検索結果先頭表示要求は、対象ページと検索クエリを持つ一回限りの状態として維持する
- [x] 2.4 `resetSearchState()` で検索モード、検索クエリ、検索結果先頭表示要求をまとめてクリアする
- [x] 2.5 検索入力の text と selection、および一回限りの検索フォーカス要求を `TabListUiState` で公開する

## 3. ViewModel の責務整理

- [x] 3.1 `TabListViewModel` から通常リスト復元用 snapshot 保存メソッドを削除する
- [x] 3.2 検索クエリ空→非空では、現在ページの検索結果先頭表示要求を発行する
- [x] 3.3 検索クエリ非空→別の非空では、新しい検索クエリ向けの検索結果先頭表示要求を発行する
- [x] 3.4 検索クエリ非空→空では、通常リスト復元要求を発行せず、検索結果先頭表示要求だけをクリアする
- [x] 3.5 検索結果先頭表示要求の consume メソッドを維持し、同じ要求が再実行されないようにする
- [x] 3.6 `TabListViewModel` で検索入力 selection と一回限りの検索フォーカス要求を管理し、enter/close/reset に連動して更新する
- [x] 3.7 `TabListViewModel` の画面 UI 状態を `TabListUiState` 単一の `MutableStateFlow` へ集約し、配列キャストを削除する

## 4. TabScreenContent のリスト分離

- [x] 4.1 板一覧・スレッド一覧それぞれに通常用と検索用の `LazyListState` を用意する
- [x] 4.2 `searchQuery.isNotBlank()` を表示切り替え条件にし、検索クエリが空の間は通常リストを表示する
- [x] 4.3 検索クエリが非空のときは検索結果リストと検索用 `LazyListState` を表示する
- [x] 4.4 検索解除時の通常リスト復元 `LaunchedEffect` を削除する
- [x] 4.5 検索結果先頭表示要求を監視し、現在 query と一致するときだけ対象ページの検索用 `LazyListState` を先頭表示する
- [x] 4.6 検索結果先頭表示後に要求を consume する
- [x] 4.7 通常リストと検索結果リストの切り替わりに短いフェードアニメーションを追加する
- [x] 4.8 `SearchInputField` を `TextFieldValue` 相当の入力 state と一回限りのフォーカス要求に対応させる
- [x] 4.9 検索結果表示中に現在ページのフィルタ結果が 0 件であれば、中央寄せの空状態メッセージを表示する
- [x] 4.10 通常リスト / 検索結果あり / 検索結果なしを単一の `AnimatedContent` で切り替える

## 4a. 共通検索入力の IME composition 保持

- [x] 4a.1 `SearchBottomBar` を `TextFieldValue` ベースの入力 state と更新コールバックを受け取る API へ変更する
- [x] 4a.2 `BoardUiState` の検索入力状態を `String` から `TextFieldValue` へ移行し、既存の検索処理向けに `searchQuery` 派生プロパティを提供する
- [x] 4a.3 `ThreadUiState` の検索入力状態を `String` から `TextFieldValue` へ移行し、既存の検索処理向けに `searchQuery` 派生プロパティを提供する
- [x] 4a.4 `BoardViewModel` / `ThreadListCoordinator` の検索更新 API を `TextFieldValue` ベースへ変更し、受け取った `composition` を含む値をそのまま保持する
- [x] 4a.5 `ThreadViewModel` の検索更新 API を `TextFieldValue` ベースへ変更し、受け取った `composition` を含む値をそのまま保持する
- [x] 4a.6 `BoardScaffold` / `ThreadScaffold` から `SearchBottomBar` へ `TextFieldValue` を渡すように変更する
- [x] 4a.7 `SearchInputField` の `String` 互換 API を削除するか、残す場合は IME composition を保持できない簡易用途であることを明確にし、板・スレ画面からは使用しない

## 5. BottomSheet の検索状態リセット

- [x] 5.1 `TabsBottomSheet` dismiss 時に検索状態を完全破棄する既存導線を維持する
- [ ] 5.2 BottomSheet 再表示時に検索モードではない初期状態で表示されることを確認する

## 6. テスト

- [x] 6.1 `TabListViewModel` の検索クエリ空→非空で検索結果先頭表示要求が発行されることをユニットテストする
- [x] 6.2 `TabListViewModel` の非空→別の非空で新しい query 向けの検索結果先頭表示要求が発行されることをユニットテストする
- [x] 6.3 `TabListViewModel` の非空→空で通常リスト復元要求が発行されないことをユニットテストする
- [x] 6.4 検索状態完全破棄で検索モード・検索クエリ・検索結果先頭表示要求がすべてクリアされることをユニットテストする
- [x] 6.5 `TabListViewModel` の検索入力更新で selection が保持され、フォーカス要求が consume 後に再発行されないことをユニットテストする
- [x] 6.7 `TabListViewModel` の単一 `TabListUiState` 更新で、検索終了時と選択解除時の関連状態がまとめて反映されることをユニットテストする
- [x] 6.8 板画面・スレッド画面の検索入力更新で `TextFieldValue.composition` を含む入力 state が保持されることをユニットテストする
- [ ] 6.6 可能であれば Compose UI テストで、検索中のスクロール後に検索解除して通常リスト位置が維持されることを検証する

## 7. 検証

- [ ] 7.1 起動直後の通常タブ一覧で、検索中にスクロールしてから検索解除しても通常リスト位置が維持されることを確認する
- [ ] 7.2 他画面からタブ一覧へ戻った後、検索解除時に通常リスト位置が維持されることを確認する
- [ ] 7.3 スレ画面または板画面から開くタブ一覧 BottomSheet で、検索解除時に通常リスト位置が維持されることを確認する
- [ ] 7.4 タブ一覧 BottomSheet を検索中に閉じて再表示したとき、検索なしの状態で開くことを確認する
- [x] 7.5 Android CI のビルドとユニットテストを実行し、成功を確認する
- [ ] 7.6 板タブ一覧・スレッドタブ一覧で検索結果が 0 件のとき、中央に空状態メッセージが表示されることを確認する
- [ ] 7.7 検索結果 0 件の状態で戻る操作をしたとき、通常リストへ戻る途中にリスト先頭が一瞬表示されないことを確認する
- [ ] 7.8 板画面・スレッド画面の検索バーで日本語入力の変換候補を選択でき、未確定文字が即確定されないことを確認する

## Superseded Notes

以前の計画では、通常リストと検索結果リストで同じ `LazyListState` を共有し、検索開始前の index/offset snapshot を ViewModel に保存して検索解除後に `requestScrollToItem` で復元する方針だった。今回の計画変更により、通常リスト用 state を検索中に変更しない設計へ切り替えるため、通常リスト復元用 snapshot / pending restore / 復元 `LaunchedEffect` は削除対象となる。
