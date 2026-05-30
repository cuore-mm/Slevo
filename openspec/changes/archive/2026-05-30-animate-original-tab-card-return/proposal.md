## Why

タブ長押し選択を解除したとき、現在は再描画された floating card が縮小してから元カードが即時復帰するため、戻り先である元カードとのつながりが弱く見える。元カード自身も長押し中の拡大状態を持ち、解除時の縮小を担当することで、選択カードが元の位置へ自然に戻る見た目にする。

## What Changes

- 長押し選択中の元カードも、透明状態のまま選択中スケールへ拡大する。
- 長押し選択解除時は、元カードを表示状態へ戻し、元カード側で選択中スケールから通常スケールへ戻るアニメーションを表示する。
- floating card は長押し中の前面表示とメニュー位置合わせを担当し、解除時の戻り縮小アニメーションの主担当から外す。
- dim overlay、長押しメニュー、詳細/固定/閉じる操作の挙動は維持する。
- **BREAKING**: なし。

## Capabilities

### New Capabilities
- なし。

### Modified Capabilities
- `tablist-ui`: タブ長押し選択中および解除時のカード拡大・復帰アニメーションの要求を追加する。

## Impact

- タブ一覧カード描画: `TabListCard` の選択中カード表示、alpha、scale animation。
- タブ一覧長押し overlay: `TabLongPressOverlayLayer` と floating card の exit 処理。
- タブ一覧呼び出し元: `OpenBoardsList` / `OpenThreadsList` の選択中カード状態受け渡し。
- テスト/検証: 長押し開始、解除、ページ切替、メニュー操作後の視覚状態確認。
