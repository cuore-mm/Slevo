## Context

現在の全般設定は `SettingsGeneralScreen` で「ダークテーマ」スイッチを直接表示し、DataStore には `dark_mode` の boolean として保存している。スレッド設定は `SettingsThreadScreen` で並び順を 2 つの `ListItem` と `RadioButton` で表示し、ミニマップ付きスクロールバー設定も同じ画面内に直書きしている。

一方で、設定画面向けには `SettingsCardWithListItems` が既に存在し、メニュー表示向けには `AnchoredOverlayMenu` と `AnchoredOverlayMenuItem` が共通部品として提供されている。Issue #475 の受け入れ条件では、全般設定・スレッド表示設定の UI をこれらの共通部品へ揃え、テーマ設定を 3 択へ拡張する必要がある。

## Goals / Non-Goals

**Goals:**
- テーマ設定を「ライト」「ダーク」「システムテーマに従う」の 3 状態としてモデル化し、既定値をシステムテーマにする。
- 全般設定とスレッド設定を `SettingsCardWithListItems` で構成し、カード内のリスト項目として表示する。
- テーマ選択と並び順選択を `AnchoredOverlayMenu` ベースの汎用選択メニューで表示し、選択後に保存してメニューを閉じる。
- 現在値を設定項目の supporting text に表示し、メニュー内の選択済み項目は右端チェックアイコン、primary color、太字で表現する。
- 既存 boolean ダークモード設定は削除し、互換読み取りや移行処理は行わない。

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

2. DataStore のテーマ設定は string key を正とし、既存 boolean key は削除する。
   - 採用案: 新しい string preference（例: `theme_mode`）に enum 名または安定した値を保存し、未保存時は既定値 `SYSTEM` を返す。既存 `dark_mode` の互換読み取り、移行、併存更新は行わない。
   - 理由: 互換性を考慮しない方針により、保存契約を 3 状態の単一 key へ単純化できる。
   - 代替案: 既存 `dark_mode` を互換読み取り用に残す。
     - 不採用理由: 旧 boolean と新 3 状態が併存し、どちらが正か分かりにくくなるため。

3. テーマ適用は「保存されたテーマ設定」から実際の darkTheme boolean を導出する。
   - 採用案: アプリルートまたは theme 適用箇所で、`SYSTEM` の場合のみ `isSystemInDarkTheme()` を利用し、`LIGHT` / `DARK` は明示値を優先する。
   - 理由: `SlevoTheme` は boolean の `darkTheme` を受け取る既存構造を維持でき、変更範囲を設定状態の導出に集中できる。
   - 代替案: `SlevoTheme` の引数自体を enum に変える。
     - 不採用理由: Preview や既存呼び出しへの影響が広く、今回の UI 改善に対して過剰な変更になる。

4. 設定項目の UI は既存 `SettingsCardWithListItems` を優先して再利用する。
   - 採用案: 全般設定ではテーマ選択の単一カード項目、スレッド設定では並び順とスクロールバー表示をカード内リスト項目として表現する。テーマと並び順の現在値は supporting text に表示する。必要であれば、選択値表示や trailing content を渡せるよう既存 spec 構造を拡張する。
   - 理由: 設定画面内の余白、カード背景、区切り線の扱いを既存部品へ集約できる。
   - 代替案: 各画面で `Card` と `ListItem` を個別に組み合わせる。
     - 不採用理由: UI 統一の目的に反し、今後の設定項目追加時に差分が再発しやすい。

5. 選択メニューは `AnchoredOverlayMenu` ベースの汎用関数として作成する。
   - 採用案: テーマ設定行および並び順設定行の bounds を取得し、クリック時に menu expanded state と anchor bounds を更新する。メニュー本体は選択肢リスト、現在値、選択時コールバックを受け取る汎用関数として実装し、選択済み項目は右端チェックアイコン、primary color、太字で表示する。
   - 理由: Issue 要件の `AnchoredOverlayMenu` 利用を満たし、既存の dismiss 契約（外側タップ・戻る）を利用できる。
   - 代替案: `DropdownMenu` またはダイアログを使う。
     - 不採用理由: 受け入れ条件が `AnchoredOverlayMenu` を明示している。

## Risks / Trade-offs

- [Risk] 既存 boolean 設定を削除するため、アップデート後に既存ユーザーのテーマがシステム既定へ変わる。→ 互換性は対象外とし、仕様上の既定値である `SYSTEM` へ統一する。
- [Risk] `SYSTEM` 選択時に OS テーマ変更へ即時追従しない。→ テーマ適用箇所で Compose の `isSystemInDarkTheme()` を評価し、保存設定が `SYSTEM` の場合に再コンポーズで反映される構成にする。
- [Risk] 行全体をアンカーにした場合、メニュー位置が広い項目中央に寄りすぎる。→ 行の trailing 側に選択値/矢印を置く場合はその要素をアンカーにするか、実機確認で許容できる位置に調整する。
- [Risk] 汎用選択メニューの抽象度を上げすぎると、テーマと並び順以外の要件まで抱え込む。→ 今回は `AnchoredOverlayMenu`、選択済み強調、チェックアイコン、dismiss、onSelect に責務を限定する。

## Migration Plan

1. `ThemeMode` 相当の 3 状態モデルを追加し、Repository / LocalDataSource に observe・set API を追加する。
2. DataStore に string 保存 key を追加し、未設定時は `SYSTEM` として扱う。既存 boolean `dark_mode` key と API は削除し、互換変換は実装しない。
3. アプリのテーマ適用箇所で `ThemeMode` から `darkTheme` を導出する。
4. `AnchoredOverlayMenu` を用いた汎用選択メニューを作成し、選択済み項目の右端チェックアイコン、primary color、太字表示を実装する。
5. 全般設定画面をカード + 汎用選択メニューへ置換し、3 択を保存できるようにする。現在値は supporting text に表示する。
6. スレッド設定画面をカード + 汎用選択メニューへ置換し、既存の並び順保存とスクロールバー表示保存を維持する。現在値は supporting text に表示する。
7. 単体テストではテーマ設定の既定値・3 状態保存・保存値の導出を確認し、UI は Preview と必要な Compose テストで表示契約を確認する。

Rollback では、旧版へ戻した場合に削除済みの `dark_mode` が参照できない可能性がある。互換性は対象外のため、必要な場合は旧版側で既定値を使う前提とする。

## Open Questions

- 汎用選択メニューを `ui/common` に置くか、設定画面専用として `ui/settings` に置くか。テーマ設定と並び順設定の両方で使うため、実装時に利用範囲で配置を決める。
