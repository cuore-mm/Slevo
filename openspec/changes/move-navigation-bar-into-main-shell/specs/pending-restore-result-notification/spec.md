## ADDED Requirements

### Requirement: Root画面構成から独立した復元結果通知
システムは復元結果SnackbarをMainShellまたはそのNavigationBarに所有させず、Root Navigationの表示画面と遷移状態に依存しない単一の通知領域で表示しなければならない（MUST）。

#### Scenario: MainShellからBoardへ遷移中に通知する
- **WHEN** 復元結果Snackbarの表示中にMainShellからBoardまたはThreadへの遷移が始まる
- **THEN** SnackbarはMainShellと一緒に退出せず、同じroot-level通知として表示を継続する

#### Scenario: Board表示中に結果が公開される
- **WHEN** BoardまたはThreadの表示中に未通知の復元結果が公開される
- **THEN** システムはMainShellへ戻ることを待たずにroot-level Snackbarを表示する

#### Scenario: MainShell固有通知と区別する
- **WHEN** MainShell内だけを対象とするSnackbarが存在する
- **THEN** システムは復元結果をそのMainShell固有通知領域へ移し替えない
