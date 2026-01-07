# Change: PostDialog投稿コントローラの共通化

## Why
PostDialogの投稿処理はThreadViewModel側とBoardViewModel側で重複しており、
状態更新やエラーハンドリングの差異が生まれやすい。共通コントローラに委譲することで
振る舞いを統一し、保守負荷を下げる。

## What Changes
- PostDialogの状態/操作/投稿フローを管理する共通コントローラを追加する
- 返信とスレ立ての差分（使用するRepository）は実行インターフェースで差し替える
- 投稿成功時の履歴記録など共通後処理をコントローラに集約する
- ThreadViewModel/BoardViewModelの投稿関連処理をコントローラへ委譲する
- UIコンポーザブル（PostDialog）は変更せず、接続のみ整理する

## Impact
- Affected specs: post-dialog（新規）
- Affected code:
  - app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadViewModel.kt
  - app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadViewModelPost.kt
  - app/src/main/java/com/websarva/wings/android/slevo/ui/board/viewmodel/BoardViewModel.kt
  - app/src/main/java/com/websarva/wings/android/slevo/ui/board/viewmodel/ThreadCreationController.kt
  - app/src/main/java/com/websarva/wings/android/slevo/ui/board/screen/BoardScaffold.kt
  - app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt
  - app/src/main/java/com/websarva/wings/android/slevo/ui/common/*（新規コントローラ配置）
