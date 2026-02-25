## Context

Thread 画面の下端プル更新は、既存実装で `overscroll` によって更新判定を行い、表示は `ContainedLoadingIndicator` の固定表示を使っている。
そのため、プル中（更新前）と更新中（`isLoading`）の見た目差が小さく、引っ張り量に応じたフィードバックが不足している。

今回の変更は更新判定ロジックを維持したまま、表示のみを拡張する。
参考実装として Material3 PullToRefresh の `distanceFraction` と回転制御（`rotate(-(progress - 1) * 180)`）の考え方を取り入れる。

## Goals / Non-Goals

**Goals:**
- プル中は引っ張り量に応じてインジケーターを左回転させる。
- プル戻し中は引っ張り量減少に連動して右回転に見えるモーションを適用する。
- プル中は引っ張り量に応じてインジケーターを段階的に拡大し、閾値で最大サイズに到達させる。
- 閾値超過後に引っ張り量を戻した場合、現在量に応じてサイズが縮小する。
- 更新中は既存どおり右回転の `ContainedLoadingIndicator` 表示を維持する。
- 既存の下端更新判定（閾値・触覚・発火条件）を変更しない。

**Non-Goals:**
- 下端更新判定の閾値・arming 条件・haptic 条件の変更。
- `BoardScreen` への横展開。
- インジケーター色やテーマトークンの刷新。

## Decisions

- **表示モードを `pulling` と `refreshing` に分離する**
  - `refreshing` (`uiState.isLoading`) では従来どおり indeterminate `ContainedLoadingIndicator` を使う。
  - `pulling` (`!isLoading && overscroll > 0f`) では determinate `ContainedLoadingIndicator(progress = { ... })` と `Modifier.graphicsLayer` を組み合わせる。
  - 代替案として単一の `ContainedLoadingIndicator(progress)` で両状態を扱う案は、更新中の既存見た目維持要件を満たしにくいため採用しない。

- **プル量正規化は「現在値ベース」で扱い、履歴固定しない**
  - `pullProgressRaw = overscroll / refreshThresholdPx`、`sizeProgress = pullProgressRaw.coerceIn(0f, 1f)` を使用する。
  - スケールは `sizeProgress` で計算し、閾値超過時は最大化するが、戻した場合は現在値に応じて縮小する。
  - 代替案として「一度閾値超過したら最大サイズ維持」方式は、戻し操作のフィードバックが消えるため採用しない。

- **回転方向は `delta` 判定ではなく、進捗由来の角度式で表現する**
  - `rotationTarget = f(pullProgressRaw)` を直接使い、`animateFloatAsState` で追従させる。
  - 角度目標が小さくなる戻し操作では、見た目上右回転となる（公式実装の progress ベース制御に近い）。
  - 代替案として `overscroll` 差分（delta）で方向を明示判定する方式は、状態分岐が増えて公式実装との乖離が大きくなるため採用しない。

- **専用インジケーター Composable を Thread 画面配下に作る**
  - `ThreadScreen` 本体には表示条件のみを置き、モーション計算は `ThreadBottomRefreshIndicator`（仮称）へ集約する。
  - 代替案として `ThreadScreen` 直書きは可読性低下と再調整コスト増のため採用しない。

## Risks / Trade-offs

- [Risk] スケールと回転の同時適用で視認性が下がる可能性。  
  Mitigation: 最小スケールを 0.7〜0.8 の範囲に制限し、最大回転角を 180 度基準で調整可能にする。

- [Risk] `overscroll` 更新タイミングにより回転方向切り替え付近で見た目が揺れる可能性。  
  Mitigation: `animateFloatAsState` で回転角を補間し、目標角を progress ベースで連続的に計算する。

- [Trade-off] 専用 Composable 追加でファイルは増えるが、表示ロジックと更新判定ロジックの責務分離を優先する。

## Migration Plan

1. 現行の `ContainedLoadingIndicator` 直描画を専用 Composable 呼び出しへ置換する。
2. `overscroll` と `refreshThresholdPx` から `pullProgressRaw` / `sizeProgress` を計算し、pulling モードの回転角とスケールを実装する。
3. refreshing モードは既存表示を維持する。
4. 閾値未満/閾値到達/閾値超過/戻し操作/更新中の状態を手動確認し、CI で回帰を確認する。

## Open Questions

- `rotationTarget` の係数（例: 180度基準）をどこまで大きくするか（視認性と過剰演出のバランス）。
