## 1. 実装
- [x] 1.1 BoardUiStateにスレッド情報シート表示用の状態（選択ThreadInfoと表示フラグ）を追加する
- [x] 1.2 BoardViewModelにシート開閉処理と選択スレッド更新処理を追加する
- [x] 1.3 BoardScreen/ThreadCardで長押しを検知してシート表示をトリガーする（通常タップ遷移は維持）
- [x] 1.4 BoardScaffoldでThreadInfoBottomSheetを表示し、dismissで状態を更新する

## 2. 検証
- [x] 2.1 CI build と unit tests が成功することを確認する
