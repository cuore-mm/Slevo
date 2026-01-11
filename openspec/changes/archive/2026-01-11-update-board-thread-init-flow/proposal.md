# Change: Board/Thread ViewModel 初期化フローの統一

## Why
BoardViewModel と ThreadViewModel の初期化手順が異なり、画面表示までの責務が分散しているため、挙動の差分や保守コストが増えている。
ViewModel の初期化シーケンスを統一し、同じ設計で読みやすく変更しやすい状態にする。

## What Changes
- BoardViewModel / ThreadViewModel の初期化シーケンス（UIState反映・データ補完・監視開始・BaseViewModel.initialize）を同じ順序に揃える
- 初期化キーの扱い（重複初期化の抑止）と強制再初期化の入り口を揃える
- 画面表示までの処理責務が明確になるように初期化処理を整理する

## Impact
- Affected specs: board-thread-init
- Affected code: `app/src/main/java/com/websarva/wings/android/slevo/ui/board/viewmodel/BoardViewModel.kt`, `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadViewModel.kt`, `app/src/main/java/com/websarva/wings/android/slevo/ui/board/screen/BoardScaffold.kt`, `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt`
