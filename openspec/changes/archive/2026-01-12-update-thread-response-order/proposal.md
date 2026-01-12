# Change: スレッドのレス表示順を新着グループ末尾追加に変更

## Why
- リロードのたびに既存レスの並びが変わってしまう問題を解消するため
- 既存の表示順を維持しつつ、新着レスだけを末尾に追加する挙動に統一するため

## What Changes
- 新着レスを「更新単位のグループ」として末尾に追加する（NUMBER/TREE 両方）
- グループ内の整形（返信ツリー、dimmed の親挿入など）は現行ロジックを踏襲する
- 新着バーは最新グループの先頭にのみ表示する（新着0件の更新では表示しない）
- グループ状態はスレッドタブを閉じるまで ViewModel 内で保持する

## Impact
- Affected specs: `thread-response-order`（新規）
- Affected code:
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadViewModel.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadDisplayTransformers.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/state/ThreadUiState.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScreen.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/components/MomentumBar.kt`
