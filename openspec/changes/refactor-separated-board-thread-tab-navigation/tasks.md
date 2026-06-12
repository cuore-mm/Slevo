## 1. 選択中タブ key の導入

- [ ] 1.1 `BoardTabsCoordinator` に選択中板タブ key（正規化済み boardUrl）を管理する StateFlow/API を追加する
- [ ] 1.2 `ThreadTabsCoordinator` に選択中スレッドタブ key（ThreadId）を管理する StateFlow/API を追加する
- [ ] 1.3 `TabSessionStore` に板/スレッドのタブ登録・選択を分離した公開 API を追加する
- [ ] 1.4 タブ削除時に selected key が削除対象だった場合、隣接タブまたは先頭タブへ補正する
- [ ] 1.5 既存 `boardCurrentPage` / `threadCurrentPage` API を移行期間の互換レイヤーへ縮小し、永続化と正本利用を廃止する

## 2. route entry と selected key の同期

- [ ] 2.1 `AppRoute.Board` / `AppRoute.Thread` の既存引数構造を維持し、引数を初期化入力・読み込み中 placeholder として扱う方針を実装箇所に反映する
- [ ] 2.2 `BoardScaffold` の route entry 時に、明示的な open 操作または selected key 未設定時だけ route を解決・正規化して板タブを登録し、選択中板タブ key を初期化する
- [ ] 2.3 `ThreadScaffold` の route entry 時に、明示的な open 操作または selected key 未設定時だけ route を解決・正規化してスレッドタブを登録し、選択中スレッドタブ key を初期化する
- [ ] 2.4 route 解決に失敗した場合は既存の無効 URL 表示と `navigateUp()` の挙動を維持する
- [ ] 2.5 route entry 同期が横スワイプ後の通常再コンポーズで selected key を上書きしないよう、Effect key と補正条件を整理する

## 3. BbsRouteScaffold の Pager 状態整理

- [ ] 3.1 `BbsRouteScaffold` の初期ページ決定を `currentPage` 優先から selected key 導出へ変更する
- [ ] 3.2 selected key に一致するタブ index が存在する場合のみ Pager を同期し、通常再コンポーズでユーザーのスワイプ位置を戻さない
- [ ] 3.3 Pager のユーザースワイプ完了時に、表示ページの tab key を selected key へ反映する
- [ ] 3.4 タブ数変更・削除後に Pager index が範囲外にならないよう補正する
- [ ] 3.5 板画面とスレッド画面で同じ補正規則を使えるよう、key 抽出と index 導出ロジックを共通化する
- [ ] 3.6 復元時に過去の currentPage/index を fallback として使わず、selected key と補正規則だけで表示タブを決定する

## 4. ナビゲーション API の責務分離

- [ ] 4.1 `navigateToBoard` / `navigateToThread` の責務を棚卸しし、タブ登録・タブ選択・画面遷移を分離した API 方針に置き換える
- [ ] 4.2 正規化済み route から板タブを登録・選択する API を追加する
- [ ] 4.3 正規化済み route からスレッドタブを登録・選択する API を追加する
- [ ] 4.4 Board route / Thread route へ遷移する API は NavController 操作だけを担当するよう整理する
- [ ] 4.5 タブ選択だけの操作で同種別 route の back stack entry が追加されないよう NavOptions を整理する
- [ ] 4.6 タブ一覧で別種別タブを選択した場合は、現在 surface を target surface へ置換し、back stack を増やさない NavOptions に固定する

## 5. 呼び出し元の移行

- [ ] 5.1 `OpenBoardsList` の板タブ選択を、板タブ selected key 更新と必要最小限の画面種別切替に変更する
- [ ] 5.2 `OpenThreadsList` のスレッドタブ選択を、スレッド selected key 更新と必要最小限の画面種別切替に変更する
- [ ] 5.3 `TabsBottomSheet` / `TabScreenContent` のタブ選択後に検索状態をリセットし、シート表示時はシートを閉じる挙動を維持する
- [ ] 5.4 URL 入力結果の処理を、タブ登録・選択と画面遷移の責務分離 API へ移行する
- [ ] 5.5 登録板一覧から板を開く処理を、板タブ登録・選択後に板画面へ遷移する流れへ移行する
- [ ] 5.6 板画面からスレッドを開く処理を、スレッドタブ登録・選択後に Thread route を back stack へ積む流れへ移行する
- [ ] 5.7 deep link、ブックマーク、履歴、スレッド情報 BottomSheet など `navigateToBoard` / `navigateToThread` 呼び出し元をすべて新 API へ移行する

## 6. 戻る操作と下ナビゲーションの確認

- [ ] 6.1 板→スレッド→戻るで、直前の板画面と選択中板タブが復元されることを確認する
- [ ] 6.2 タブ一覧シートから同種別タブを選択しても back stack が増えず、戻る操作でタブ一覧シートへ戻らないことを確認する
- [ ] 6.3 タブ一覧シートから別種別タブを選択した場合、現在 surface が置換され、戻る操作で選択前 surface に戻らないことを確認する
- [ ] 6.4 下ナビゲーションの Tabs / Bookmark / 掲示板 / 設定の表示条件と restoreState 挙動が維持されていることを確認する

## 7. テストと検証

- [ ] 7.1 selected key の追加・削除・補正に関する coordinator 単体テストを追加または更新する
- [ ] 7.2 BbsRouteScaffold の selected key から Pager index を導出するロジックをテスト可能な関数として検証する
- [ ] 7.3 URL 入力、スレッドリンク、route 正規化の既存テストを新 API 方針に合わせて更新する
- [ ] 7.4 手動で「登録板一覧→板→スレッド→戻る」「板→シート→別板」「スレッド→シート→別スレッド」「URL入力→板/スレッド」を確認する
- [ ] 7.5 CI の Android build/test workflow を実行し、成功を確認する

## 8. クリーンアップ

- [ ] 8.1 互換レイヤーとして残した currentPage API と永続化を削除または private 化する
- [ ] 8.2 route と selected key の二重同期を引き起こす古い Effect や helper を削除する
- [ ] 8.3 新規・変更した class / interface / non-trivial function に KDoc と必要なコメントを追加する
- [ ] 8.4 未使用 import、不要な NavOptions、削除済み helper 参照を整理する
