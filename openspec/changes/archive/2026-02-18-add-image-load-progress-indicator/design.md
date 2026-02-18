## Context

Issue #444 は、画像サムネイルと画像ビューア画面で「読み込み中」だけでなく進捗が分かる表示を求めている。現状は `SubcomposeAsyncImage` の loading スロットによる無段階インジケータ、または進捗表示なしの実装が混在しており、ユーザーは完了見込みを判断しづらい。

本変更は UI コンポーネント単体では完結せず、Coil/OkHttp 側での受信進捗取得と Compose への状態伝搬をまたぐ横断変更になる。加えて、既存のタップ遷移・shared transition・バー表示切替などの既存契約を維持する必要がある。

## Goals / Non-Goals

**Goals:**
- 画像サムネイル（`ImageThumbnailGrid` と `ImageViewerThumbnailBar`）で読み込み進捗を視覚化する。
- 画像ビューア画面（表示中の主画像）で読み込み進捗を視覚化する。
- 進捗取得不可時のフォールバック表示（無段階インジケータ）を定義する。
- 実装順を「ImageThumbnailGrid -> 画像ビューア画面」として段階導入できる計画にする。

**Non-Goals:**
- 画像デコード処理そのものの高速化。
- 画像保存/共有フローの仕様変更。
- 既存の shared transition キーや画面遷移契約の変更。

## Decisions

### 1) 進捗情報は画像読み込み基盤で一元取得する
- **Decision**: Coil が使う OkHttp 通信経路に進捗計測を追加し、URL 単位で `0.0..1.0` の進捗値を購読できる共通ストアを用意する。
- **Rationale**: 各 Composable で個別に進捗計測すると重複実装と不整合が発生しやすい。基盤側で一元化するとサムネイルとビューアで同じ契約を使える。
- **Alternatives considered**:
  - `onLoading/onSuccess` だけで対応: 進捗率を表現できないため不採用。
  - 画面ごとに独立したローカル実装: 同一 URL の二重計測・契約分岐が増えるため不採用。

### 2) UI 適用は段階的に行う
- **Decision**: 第1段階で `ImageThumbnailGrid` に進捗表示を導入し、その後に `ImageViewerPager` と `ImageViewerThumbnailBar` へ同等契約を適用する。
- **Rationale**: ユーザー要望の実装順に合わせ、影響範囲を小さく分割して検証しやすくする。
- **Alternatives considered**:
  - 同時実装: 変更点が広がり、回帰時の切り分けが難しくなるため不採用。

### 3) 進捗不明時はフォールバック表示に統一する
- **Decision**: `Content-Length` 不明、キャッシュヒット等で進捗率が算出できない場合は無段階インジケータを表示する。
- **Rationale**: 取得不能ケースでも「読み込み中」であることを一貫して示せる。
- **Alternatives considered**:
  - 進捗不明時は表示しない: 体感上の停止と区別できず UX が悪化するため不採用。

## Risks / Trade-offs

- [Risk] 進捗イベント頻度が高く再描画コストが増える -> [Mitigation] 進捗更新を最小単位で間引きし、表示は必要時のみ購読する。
- [Risk] 同一 URL の並行リクエストで状態競合する -> [Mitigation] リクエスト識別子を導入し、最新リクエストのみ反映する。
- [Risk] キャッシュ経由で進捗表示が一瞬で消える挙動差 -> [Mitigation] 即時成功時はインジケータ非表示へ遷移する契約を明記して UI ちらつきを抑える。

## Migration Plan

1. 進捗計測基盤を追加し、既存読み込み契約を壊さない形で無効時フォールバックを用意する。
2. `ImageThumbnailGrid` に進捗インジケータを導入し、サムネイル遷移可否ロジックと共存させる。
3. `ImageViewerPager` に進捗インジケータを導入し、バー表示切替やタップ操作契約を維持する。
4. `ImageViewerThumbnailBar` に進捗インジケータを導入し、既存の選択中サムネイル強調と競合しない構成にする。
5. 既存機能（共有トランジション、メニュー、画像切替）の非回帰確認を行う。

## Open Questions

- なし（issue 444 の「画像サムネイル」は `ImageViewerThumbnailBar` を含める方針で確定）。
