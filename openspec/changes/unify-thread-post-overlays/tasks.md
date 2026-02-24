## 1. 投稿オーバーレイの統合

- [x] 1.1 `ThreadScreen` から `PostMenuSheet` / `PostItemDialogs` の表示責務を除去し、イベント通知のみ残す
- [x] 1.2 `ThreadScaffold` に統合ホストを追加し、主投稿とポップアップのメニュー要求を一元管理する

## 2. 接続更新と確認

- [x] 2.1 `ThreadScreen` から `ThreadScaffold` へ `PostDialogTarget` 通知を接続する
- [x] 2.2 `ReplyPopup` の既存通知経路を統合ホストへ接続する
- [x] 2.3 主投稿/ポップアップのメニューとダイアログが同一挙動であることを確認する
- [ ] 2.4 build と unit test を実行し、統合による回帰がないことを確認する
