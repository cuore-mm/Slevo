## Context

現在の全般設定は `SettingsGeneralScreen` で「ダークテーマ」スイッチを直接表示し、DataStore には `dark_mode` の boolean として保存している。スレッド設定は `SettingsThreadScreen` で並び順を 2 つの `ListItem` と `RadioButton` で表示し、ミニマップ付きスクロールバー設定も同じ画面内に直書きしている。

一方で、設定画面向けには `SettingsCardWithListItems` が既に存在し、メニュー表示向けには `AnchoredOverlayMenu` と `AnchoredOverlayMenuItem` が共通部品として提供されている。Issue #475 の受け入れ条件では、全般設定・スレッド表示設定の UI をこれらの共通部品へ揃え、テーマ設定を 3 択へ拡張する必要がある。

## Goals / Non-Goals

**Goals:**
- テーマ設定を「ライト」「ダーク」「システムテーマに従う」の 3 状態としてモデル化し、既定値をシステムテーマにする。
- 全般設定とスレッド設定を `SettingsCardWithListItems` で構成し、カード内のリスト項目として表示する。
- テーマ選択と並び順選択を `AnchoredOverlayMenu` で表示し、選択後に保存してメニューを閉じる。
- 既存ユーザーの boolean ダークモード設定を、可能な限り同等の明示テーマ選択へ移行する。

**Non-Goals:**
- アプリ全体のカラーパレット、タイポグラフィ、MaterialTheme 定義の刷新。
- スレッド表示設定の種類追加や並び順ロジックの変更。
- `AnchoredOverlayMenu` 自体の配置アルゴリズムや見た目の再設計。
- 設定画面トップバーやジェスチャー設定画面の既存メニュー契約の変更。

## Decisions

1. テーマ設定は boolean ではなく enum で表現する。
   - 採用案: `LIGHT` / `DARK` / `SYSTEM` のような enum または同等の型を data/model または theme/settings 層に追加し、UI state も同じ 3 状態を保持する。
   - 理由: UI・保存・テーマ適用の各層で 3 択を明示でき、boolean の組み合わせによる不正状態を避けられる。
   - 代替案: `isDark: Boolean?` で null をシステム扱いにする。
     - 不採用理由: null の意味が呼び出し側へ漏れ、表示文言や保存値の扱いが分かりにくい。

2. DataStore のテーマ設定は string key を正とし、既存 boolean key は互換読み取りに使う。
   - 採用案: 新しい string preference（例: `theme_mode`）に enum 名または安定した値を保存し、未保存時は既定値 `SYSTEM` を返す。既存 `dark_mode` が存在する場合は、初回読み取りまたは migration で `DARK` / `LIGHT` に対応させる。
   - 理由: 新規ユーザーの既定値をシステムテーマにしつつ、既存ユーザーが明示的に選んだライト/ダーク体験を維持できる。
   - 代替案: 既存 `dark_mode` を削除して全ユーザーを `SYSTEM` にする。
     - 不採用理由: 既存ユーザーの選択が失われ、アップデート後に見た目が意図せず変わる可能性がある。

3. テーマ適用は「保存されたテーマ設定」から実際の darkTheme boolean を導出する。
   - 採用案: アプリルートまたは theme 適用箇所で、`SYSTEM` の場合のみ `isSystemInDarkTheme()` を利用し、`LIGHT` / `DARK` は明示値を優先する。
   - 理由: `SlevoTheme` は boolean の `darkTheme` を受け取る既存構造を維持でき、変更範囲を設定状態の導出に集中できる。
   - 代替案: `SlevoTheme` の引数自体を enum に変える。
     - 不採用理由: Preview や既存呼び出しへの影響が広く、今回の UI 改善に対して過剰な変更になる。

4. 設定項目の UI は既存 `SettingsCardWithListItems` を優先して再利用する。
   - 採用案: 全般設定ではテーマ選択の単一カード項目、スレッド設定では並び順とスクロールバー表示をカード内リスト項目として表現する。必要であれば、選択値表示や trailing content を渡せるよう既存 spec 構造を拡張する。
   - 理由: 設定画面内の余白、カード背景、区切り線の扱いを既存部品へ集約できる。
   - 代替案: 各画面で `Card` と `ListItem` を個別に組み合わせる。
     - 不採用理由: UI 統一の目的に反し、今後の設定項目追加時に差分が再発しやすい。

5. 選択メニューは項目行をアンカーとして `AnchoredOverlayMenu` を表示する。
   - 採用案: テーマ設定行および並び順設定行の bounds を取得し、クリック時に menu expanded state と anchor bounds を更新する。メニュー内は `AnchoredOverlayMenuItem` で選択肢を表示し、現在値は文言または選択マークで分かるようにする。
   - 理由: Issue 要件の `AnchoredOverlayMenu` 利用を満たし、既存の dismiss 契約（外側タップ・戻る）を利用できる。
   - 代替案: `DropdownMenu` またはダイアログを使う。
     - 不採用理由: 受け入れ条件が `AnchoredOverlayMenu` を明示している。

## Risks / Trade-offs

- [Risk] 既存 boolean 設定から新 string 設定への移行漏れで、既存ユーザーのテーマがシステム既定へ変わる。→ DataStore 読み取りまたは migration に互換処理を含め、既存 key がある場合は明示テーマへ変換する。
- [Risk] `SYSTEM` 選択時に OS テーマ変更へ即時追従しない。→ テーマ適用箇所で Compose の `isSystemInDarkTheme()` を評価し、保存設定が `SYSTEM` の場合に再コンポーズで反映される構成にする。
- [Risk] 行全体をアンカーにした場合、メニュー位置が広い項目中央に寄りすぎる。→ 行の trailing 側に選択値/矢印を置く場合はその要素をアンカーにするか、実機確認で許容できる位置に調整する。
- [Risk] `SettingsCardWithListItems` の既存 switch 用ファクトリが選択メニュー項目に合わない。→ `ListItemSpec` を直接組み立てるか、小さな選択項目用ファクトリを追加して既存 switch 挙動を壊さない。

## Migration Plan

1. `ThemeMode` 相当の 3 状態モデルを追加し、Repository / LocalDataSource に observe・set API を追加する。
2. DataStore に string 保存 key を追加し、未設定時は `SYSTEM`、既存 boolean key がある場合は `LIGHT` / `DARK` として扱う。
3. アプリのテーマ適用箇所で `ThemeMode` から `darkTheme` を導出する。
4. 全般設定画面をカード + アンカー付きメニューへ置換し、3 択を保存できるようにする。
5. スレッド設定画面をカード + アンカー付きメニューへ置換し、既存の並び順保存とスクロールバー表示保存を維持する。
6. 単体テストではテーマ設定の既定値・互換読み取り・保存値の導出を確認し、UI は Preview と必要な Compose テストで表示契約を確認する。

Rollback は、新 string key を読まない旧版へ戻した場合に旧 boolean key だけが参照される点に注意する。移行時に旧 boolean key を即削除しないことで、旧版でも最後の明示ライト/ダーク設定を参照できる余地を残す。

## Open Questions

- 現在値の表示は「テーマ: システムテーマに従う」のように supporting text で出すか、trailing text と矢印で出すか。実装時は既存設定カードの見た目に合わせて決める。
- メニュー内の現在選択中項目にチェックアイコンを表示するか、選択済みテキストの強調のみとするか。アクセシビリティ上は選択状態が分かる表現を含める。
