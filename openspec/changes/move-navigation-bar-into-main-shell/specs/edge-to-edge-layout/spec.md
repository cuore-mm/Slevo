## ADDED Requirements

### Requirement: Root overlayをアプリ内下部chromeから保護する
システムはroot-level SnackbarなどのRoot overlayを、表示中画面のNavigationBarまたはBoard / Thread下部ツールバーによって文字と操作が遮られない位置へ表示しなければならない（MUST）。Root overlayの下端位置調整によってRoot Navigation contentの測定領域を変更してはならない（MUST NOT）。

#### Scenario: MainShell上でRoot Snackbarを表示する
- **WHEN** NavigationBarを持つMainShell上でroot-level Snackbarを表示する
- **THEN** Snackbarの文字と操作はNavigationBarおよびsystem navigation領域に遮られない
- **AND** MainShell contentの測定boundsはSnackbarの表示によって変化しない

#### Scenario: Board上でRoot Snackbarを表示する
- **WHEN** 下部ツールバーを持つBoardまたはThread上でroot-level Snackbarを表示する
- **THEN** Snackbarの文字と操作は下部ツールバーおよびsystem navigation領域に遮られない

#### Scenario: 下部chromeが異なる画面へ遷移する
- **WHEN** root-level Snackbarを表示したままMainShellとBoardまたはThreadの間を遷移する
- **THEN** システムは表示中画面の下部chromeを回避するようSnackbarだけを再配置する
- **AND** Root Navigation画面の利用可能boundsを途中で変更しない
