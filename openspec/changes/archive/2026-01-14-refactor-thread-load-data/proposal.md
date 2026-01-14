# Change: ThreadViewModel#loadData の責務分離リファクタ

## Why
ThreadViewModel の loadData は取得・派生計算・UI更新・履歴処理を一括で扱っており、責務が集中して読み解きと変更が難しくなっている。機能追加や不具合対応時の影響範囲を小さくするため、処理の分割と構造化が必要。

## What Changes
- loadData を取得/派生計算/UI反映/履歴処理の小さな関数に分割する
- 成功時に必要な派生データをまとめるデータ構造を導入する
- 振る舞いは現状維持とし、ユーザー向けの挙動変更は行わない

## Impact
- Affected specs: thread-load-state（新規）
- Affected code: app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadViewModel.kt
