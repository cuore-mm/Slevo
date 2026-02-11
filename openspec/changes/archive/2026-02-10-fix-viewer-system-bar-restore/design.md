## Context

画像ビューアは `WindowInsetsControllerCompat` を使ってシステムバーの表示/非表示とアイコン外観を制御している。現状は `onDispose` でアイコン外観とコントラストのみを復元し、システムバー可視状態を復元していないため、ビューアでバーを隠したまま戻ると他画面へ immersive 状態が漏れる。

## Goals / Non-Goals

**Goals:**
- 画像ビューア終了時に、入場前のシステムバー可視状態を確実に復元する。
- 既存のバー表示トグル、スクラム描画、アイコン外観制御を壊さない。
- 画面遷移先への UI 状態リークを防止する。

**Non-Goals:**
- バー表示トグルのUX変更（表示/非表示ルール変更）は行わない。
- 画像切替・サムネイル同期ロジックは変更しない。
- システムバー色の実装方針（edge-to-edge前提）は変更しない。

## Decisions

- Decision 1: `DisposableEffect(activity)` 開始時に「入場前の system bars 可視状態」を取得して保持する。  
  - 理由: `onDispose` で復元先を明確にし、ビューア固有状態が他画面へ漏れないようにするため。  
  - 代替案: 常に `show(systemBars)` を呼ぶ。  
  - 不採用理由: もともと immersive で入場したケースを壊すため。

- Decision 2: `onDispose` では既存の外観復元に加えて、保存した可視状態に応じて `show/hide(systemBars)` を実行する。  
  - 理由: 外観復元と可視状態復元を同時に完結させ、破棄時に副作用を残さないため。

- Decision 3: 可視状態が取得できない場合は `visible=true` をデフォルトとする。  
  - 理由: 通常画面の期待値（バーが見える）を優先し、UX 退行を避けるため。

## Risks / Trade-offs

- [Risk] OEM実装差で `RootWindowInsets` が一時的に `null` になる → Mitigation: `null` 時は `visible=true` へフォールバック。
- [Risk] `onDispose` 復元が他の同時制御と競合する → Mitigation: 復元対象を system bars の可視状態と既存外観項目に限定し、他ロジックへ侵襲しない。
- [Trade-off] 破棄時に `show/hide` を追加する分だけ副作用が増える → ただし影響は画面終了時1回で、リーク防止効果が上回る。
