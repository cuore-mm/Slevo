# Change: ポップアップ復帰時の表示アニメーション抑制

## Why
画像ビューアからスレッド画面へ戻った際、既に開いているレスポップアップが再表示アニメーションを行い、瞬間的にチラついて見えるため、視覚的な違和感が発生している。

## What Changes
- 画面遷移から復帰した場合に、既存ポップアップの表示アニメーションを抑制する
- 新規に追加されたポップアップは従来通りアニメーションを行う

## Impact
- Affected specs: thread-tree-popup
- Affected code: ReplyPopup, ThreadScreen, ThreadViewModel（ポップアップ表示状態とアニメーション制御）
