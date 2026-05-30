## Context

スレッドの作成日時と勢いは、スレッドキーが UNIX 秒由来の値である場合に `threadKey` とレス数から導出できる。現在は `ThreadListParser`、`BoardRepository`、`ThreadViewModel` で似た計算が個別に実装されており、タブ一覧のスレッド詳細 BottomSheet では `ThreadTabInfo` から `ThreadInfo` を組み立てる際に日付と勢いを補完していない。

`ThreadInfoBottomSheet` は `ThreadInfo.date` と `ThreadInfo.momentum` を表示する共通 UI だが、タブ詳細経路では `ThreadInfo` のデフォルト値（0 年 0 月 0 日、勢い 0.0）が渡る。DB には日付を保存していないが、thread key から同じ規則で再計算できるため、永続化項目を増やさずに補完できる。

## Goals / Non-Goals

**Goals:**

- スレッドキーから導出する作成日時・勢いの計算規則を 1 箇所に集約する。
- 板一覧、スレッド画面、subject.txt パース、タブ詳細 BottomSheet が同じ計算規則を使う。
- タブ一覧のスレッド詳細 BottomSheet で、対象スレッドの作成日時と勢いを表示する。
- 無効な thread key、非 epoch 型 key、レス数 0 の扱いを既存挙動と互換にする。
- 計算ロジックを単体テストしやすい形にする。

**Non-Goals:**

- DB スキーマを変更して日付や勢いを保存すること。
- `ThreadInfoBottomSheet` のレイアウトやアクション構成を変更すること。
- 勢い計算式そのものを刷新すること。
- thread key から導出できない掲示板形式に対して別ソースの日付を取得すること。

## Decisions

### Decision: `data/util` に派生情報計算ユーティリティを追加する

`ThreadInfoDerivedCalculator` のような専用ユーティリティを `data/util` 配下に追加し、`threadKey` と `resCount` から `ThreadDate` と `momentum` を返す。

理由:

- 既存の計算箇所は data 層と ui/viewmodel 層にまたがるため、UI 専用ではなく data/util に置くと参照方向が自然になる。
- `ThreadListParser` からも利用でき、パース時の `ThreadInfo` 生成と画面表示時の補完を同じ規則にできる。
- DB や Repository に責務を寄せると、保存しない派生値を永続状態のように扱ってしまう。

代替案:

- `ThreadListParser.calculateThreadDate()` を拡張する案: パーサ責務に勢い計算と汎用判定が残り、タブ詳細や ViewModel から Parser を参照し続ける構造になるため避ける。
- `ThreadInfo` の拡張関数にする案: `nowSeconds` を使う勢い計算が隠れ、副作用のないデータモデルとしての見通しが悪くなるため避ける。

### Decision: 無効 key では例外を投げずデフォルト派生情報を返す

共通ユーティリティは、`threadKey.toLongOrNull()` が失敗する場合、`1 until THREAD_KEY_THRESHOLD` に入らない場合、またはレス数が 0 以下の場合に、既存挙動と同じデフォルト値を返す。

理由:

- 画面表示・パース・Repository の各経路で安全に呼び出せる。
- タブ詳細 BottomSheet 表示時に不正 key でクラッシュさせない。
- 既存の `ThreadDate(0, 0, 0, 0, 0, "")` と勢い `0.0` の扱いを維持できる。

### Decision: `nowSeconds` を引数に受け取れる API にする

勢い計算は現在時刻に依存するため、共通ユーティリティは `nowSeconds` を任意引数として受け取る。

理由:

- subject.txt パースや板一覧では取得時刻・キャッシュ時刻に基づく現在時刻を渡せる。
- 単体テストで固定時刻を使い、勢い計算を安定して検証できる。
- 呼び出し側が時刻基準を選べるため、既存の `BoardRepository` の `lastFetchedAt` 基準も維持できる。

### Decision: タブ詳細では保存せず表示時に補完する

タブ詳細 BottomSheet で `ThreadInfo` を組み立てる際、`threadTab.threadKey` と `threadTab.resCount` から共通ユーティリティで派生情報を計算して渡す。

理由:

- 日付は thread key から決定でき、DB に保存する必要がない。
- 勢いは現在時刻に依存する表示値であり、永続化すると古くなる。
- `ThreadTabInfo` と `thread_states` は正本としてタイトル・最新レス数・板情報を持ち、派生値は表示時に計算するという役割分担を保てる。

## Risks / Trade-offs

- [Risk] `ThreadListParser.calculateThreadDate()` の削除や移動で既存参照が壊れる可能性がある → 呼び出し箇所をすべて共通ユーティリティへ置き換え、必要なら一時的な委譲関数を残す。
- [Risk] `BoardRepository` は `lastFetchedAt` を現在時刻として使っているため、単純に `System.currentTimeMillis()` に置き換えると勢い表示が変わる → 共通ユーティリティに `nowSeconds` を渡せるようにして既存基準を維持する。
- [Risk] タブ詳細で現在時刻基準の勢いを表示すると、板一覧のキャッシュ時刻基準と値が少し異なる場合がある → タブ詳細は表示時補完のため現在時刻基準を使い、レス数は共通状態の最新レス数を使うことを明示する。
- [Risk] 無効 key のデフォルト日付が `0年0月0日` と表示される既存 UI の見え方は残る → 本変更では既存挙動維持を優先し、非表示化や表示文言変更は別変更とする。

## Migration Plan

- DB migration は実施しない。
- 既存データはそのまま利用し、表示時に `threadKey` とレス数から派生情報を計算する。
- ロールバック時は共通ユーティリティ利用箇所を既存の個別計算に戻すだけで、データ互換性への影響はない。

## Open Questions

- 無効 key の場合に `0年0月0日` を表示する既存仕様を将来的に非表示へ変えるかは、本変更の範囲外として別途検討する。
